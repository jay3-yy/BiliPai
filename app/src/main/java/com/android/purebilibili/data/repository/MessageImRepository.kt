package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import com.android.purebilibili.core.network.grpc.ProtoWire
import com.android.purebilibili.data.model.response.SessionAccountInfo
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.data.model.response.SessionMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class ImMenuItem(val title: String, val type: Int, val url: String, val icon: String)
data class ImSession(
    val key: String,
    val id: ByteArray,
    val item: SessionItem,
    val folderPage: Int? = null,
    val chatUrl: String = "",
    val summary: String = "",
    val unreadLabel: String = "",
    val unreadDot: Boolean = false,
    val isPrivate: Boolean = false,
    val systemNotice: Boolean = false,
    val profileMid: Long = 0L,
    val pendantUrl: String = "",
    val officialType: Int? = null,
    val vipColor: Boolean = false,
    val userLabel: String = "",
)
data class ImSessionPage(
    val sessions: List<ImSession>,
    val pagination: ByteArray,
    val hasMore: Boolean,
    val menu: List<ImMenuItem>,
    val outside: List<ImMenuItem>,
)
data class ImSettingOption(val type: Int, val text: String, val selected: Boolean, val wire: ByteArray)
data class ImSetting(
    val key: Int,
    val wire: ByteArray,
    val kind: Int,
    val title: String,
    val subtitle: String = "",
    val enabled: Boolean = false,
    val summary: String = "",
    val pageType: Int? = null,
    val url: String = "",
    val options: List<ImSettingOption> = emptyList(),
)
data class ImSettingsPage(val title: String, val settings: List<ImSetting>)
data class ImBlockedKeywords(val words: List<String>, val listLimit: Int, val charLimit: Int, val hint: String)

/** 私信角标：style 0=不显示 1=红点 2=数字，numberShow 是服务端格式化好的文本。 */
data class ImTotalUnread(
    val style: Int,
    val number: Long,
    val numberShow: String,
)

/** 消息中心：总未读 + 回复/@/赞/系统通知四项，来自官方消息中心同一条 GetTotalUnread。 */
data class ImMessageCenterUnread(
    val total: Int,
    val reply: Int,
    val at: Int,
    val like: Int,
    val sysMsg: Int,
)


/** IM 页码、会话身份和不透明分页参数均以服务端为准，不混用网页列表的分类编号。 */
internal object MessageImRepository {
    private const val SERVICE = "/bilibili.app.im.v1.im/"
    /** 消息中心 GetTotalUnread 的备用官方服务。 */
    private const val INTERFACE_SERVICE = "/bilibili.im.interface.v1.ImInterface/"
    /** 官方 6.5.0 首页角标实际走的网关服务。 */
    private const val GATEWAY_SERVICE = "/bilibili.im.gateway.interface.v1.ImGatewayApi/"
    private suspend fun <T> request(method: String, message: ByteArray = byteArrayOf(), parse: (ByteArray) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { parse(BiliGrpcClient.request(SERVICE + method, message)) }
                .onFailure { if (it is CancellationException) throw it }
        }

    private suspend fun <T> interfaceRequest(method: String, message: ByteArray, parse: (ByteArray) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { parse(BiliGrpcClient.request(INTERFACE_SERVICE + method, message)) }
                .onFailure { if (it is CancellationException) throw it }
        }

    private suspend fun <T> gatewayRequest(method: String, message: ByteArray, parse: (ByteArray) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { parse(BiliGrpcClient.request(GATEWAY_SERVICE + method, message)) }
                .onFailure { if (it is CancellationException) throw it }
        }

    suspend fun sessions(pageType: Int = 1, pagination: ByteArray = byteArrayOf()): Result<ImSessionPage> =
        request(
            if (pageType == 1) "SessionMain" else "SessionSecondary",
            ProtoWire.message(ProtoWire.bytes(2, pagination), if (pageType == 1) byteArrayOf() else ProtoWire.int32(3, pageType)),
        ) { parseSessions(it, pageType) }

    internal fun parseSessions(bytes: ByteArray, pageType: Int): ImSessionPage {
        val fields = ImFields(bytes)
        val primary = pageType == 1
        val next = fields.bytes(1)
        return ImSessionPage(
            sessions = fields.all(if (primary) 5 else 3).mapNotNull { parseSession(it.bytes) },
            pagination = next,
            hasMore = ImFields(next).number(2) != 0L,
            menu = fields.all(if (primary) 6 else 4).map { parseMenu(it.bytes) },
            outside = fields.all(if (primary) 7 else 5).map { parseMenu(it.bytes) },
        )
    }

    suspend fun clearUnread(pageType: Int, session: ImSession? = null): Result<Unit> = request(
        "ClearUnread", ProtoWire.message(ProtoWire.int32(1, pageType), ProtoWire.bytes(2, session?.id ?: byteArrayOf())),
    ) { Unit }.onSuccess { MessageRepository.notifyReadStateChanged() }

    /** 私信未读总数；进消息中心不会改变它，首页角标的清空是本地行为。 */
    suspend fun totalUnread(): Result<ImTotalUnread> = request("GetTotalUnread") { bytes ->
        val unread = ImFields(ImFields(bytes).bytes(1))
        ImTotalUnread(
            style = unread.number(1).toInt(),
            number = unread.number(2),
            numberShow = unread.text(3),
        )
    }

    @Volatile
    private var gatewayUnavailable = false

    /** 消息中心未读：官方 gateway 优先，失败回退 ImInterface。 */
    suspend fun messageCenterUnread(): Result<ImMessageCenterUnread> {
        if (!gatewayUnavailable) {
            val gateway = gatewayRequest(
                "GetTotalUnread",
                ProtoWire.message(
                    ProtoWire.int32(1, 0),
                    ProtoWire.int32(2, 1),
                    ProtoWire.int32(3, 0),
                ),
            ) { parseMessageCenter(it) }
            if (gateway.isSuccess) return gateway
            gatewayUnavailable = true
        }
        return interfaceRequest(
            "GetTotalUnread",
            ProtoWire.message(ProtoWire.int32(1, 0), ProtoWire.int32(2, 1)),
        ) { parseMessageCenter(it) }
    }

    private fun parseMessageCenter(bytes: ByteArray): ImMessageCenterUnread {
        val rsp = ImFields(bytes)
        val feed = ImFields(rsp.bytes(2)).all(1).mapNotNull { entry ->
            val item = ImFields(entry.bytes)
            val key = item.text(1)
            if (key.isBlank()) null else key to item.number(2).toInt().coerceAtLeast(0)
        }.toMap()
        val newTotal = ImFields(rsp.bytes(6))
        val single = ImFields(rsp.bytes(1))
        val feedTotal = listOf("reply", "at", "like", "sys_msg").sumOf { feed[it] ?: 0 }
        val badgePrivateTotal = (single.number(1) + single.number(2)).toInt().coerceAtLeast(0)
        val total = if (newTotal.number(1) > 0) newTotal.number(1).toInt() else badgePrivateTotal + feedTotal
        return ImMessageCenterUnread(
            total = total,
            reply = feed["reply"] ?: 0,
            at = feed["at"] ?: 0,
            like = feed["like"] ?: 0,
            sysMsg = feed["sys_msg"] ?: 0,
        )
    }

    /** 官方已读上报：ImInterface/UpdateAck；Web 的 update_ack 不动 IM 后端未读。 */
    suspend fun updateAck(sessionType: Int, talkerId: Long, ackSeqno: Long): Result<Unit> = interfaceRequest(
        "UpdateAck",
        ProtoWire.message(
            ProtoWire.int64(1, talkerId),
            ProtoWire.int32(2, sessionType),
            ProtoWire.int64(3, ackSeqno),
        ),
    ) { Unit }

    suspend fun clearList(pageType: Int): Result<Unit> = request("DeleteSessionList", ProtoWire.int32(1, pageType)) { Unit }

    suspend fun pin(session: ImSession): Result<Unit> = request(
        if (session.item.top_ts > 0) "UnpinSession" else "PinSession", ProtoWire.bytes(1, session.id),
    ) { Unit }

    suspend fun settings(type: Int = 0): Result<ImSettingsPage> = request("GetImSettings", ProtoWire.int32(1, type)) { bytes ->
        val fields = ImFields(bytes)
        ImSettingsPage(fields.text(1).ifBlank { "消息设置" }, fields.all(2).map { entry ->
            val map = ImFields(entry.bytes)
            parseSetting(map.number(1).toInt(), map.bytes(2))
        }.sortedBy { it.key })
    }

    suspend fun setSetting(setting: ImSetting, enabled: Boolean? = null, selectedType: Int? = null): Result<Unit> {
        val payload = runCatching { buildSettingRequest(setting, enabled, selectedType) }.getOrElse { return Result.failure(it) }
        return request("SetImSettings", payload) { Unit }
    }

    internal fun buildSettingRequest(setting: ImSetting, enabled: Boolean? = null, selectedType: Int? = null): ByteArray {
        val root = ImFields(setting.wire)
        val updated = when (setting.kind) {
            1 -> root.replace(1, ImFields(root.bytes(1)).replace(1, ProtoWire.bool(1, requireNotNull(enabled)), nested = false))
            2 -> root.replace(2, encodeOptions(setting.options, requireNotNull(selectedType), 1))
            3 -> {
                val redirect = ImFields(root.bytes(3))
                val window = ImFields(redirect.bytes(7))
                val options = encodeOptions(setting.options, requireNotNull(selectedType), 2)
                val selected = setting.options.first { it.type == selectedType }.text
                root.replace(3, ImFields(redirect.replace(7, ProtoWire.message(
                    ProtoWire.string(1, window.text(1)), options,
                ))).replace(5, ProtoWire.string(5, selected), nested = false))
            }
            else -> error("此设置无法修改")
        }
        return ProtoWire.bytes(1, ProtoWire.message(
            ProtoWire.int32(1, setting.key), ProtoWire.bytes(2, updated),
        ))
    }

    suspend fun keywords(): Result<ImBlockedKeywords> = request("KeywordBlockingList") { bytes ->
        val fields = ImFields(bytes)
        ImBlockedKeywords(fields.all(1).map { ImFields(it.bytes).text(1) }, fields.number(2).toInt(), fields.number(3).toInt(), fields.text(4))
    }
    suspend fun addKeyword(word: String): Result<Unit> = request("KeywordBlockingAdd", ProtoWire.string(1, word)) { Unit }
    suspend fun removeKeyword(word: String): Result<Unit> = request("KeywordBlockingDelete", ProtoWire.string(1, word)) { Unit }

    private fun encodeOptions(options: List<ImSettingOption>, selectedType: Int, field: Int): ByteArray =
        ProtoWire.message(*options.map { option ->
            ProtoWire.bytes(field, ImFields(option.wire).replace(3, ProtoWire.bool(3, option.type == selectedType), nested = false))
        }.toTypedArray())

    internal fun parseSetting(key: Int, bytes: ByteArray): ImSetting {
        val root = ImFields(bytes)
        val kind = root.fields.lastOrNull { it.number in 1..4 }?.number ?: 0
        val value = ImFields(root.bytes(kind))
        val redirectPage = ImFields(value.bytes(1))
        val childSummary = if (kind == 3) redirectPage.all(1).asSequence().map { entry ->
            val child = ImFields(entry.bytes)
            parseSetting(child.number(1).toInt(), child.bytes(2))
        }.mapNotNull { child ->
            if (child.kind == 1 && child.enabled) child.title else child.options.firstOrNull { it.selected }?.text
        }.firstOrNull().orEmpty() else ""
        val options = when (kind) { 2 -> value.all(1); 3 -> ImFields(value.bytes(7)).all(2); else -> emptyList() }
            .map { field -> val option = ImFields(field.bytes)
                ImSettingOption(option.number(1).toInt(), option.text(2), option.number(3) != 0L, field.bytes)
            }
        return ImSetting(
            key = key, wire = bytes, kind = kind,
            title = when (kind) { 1 -> value.text(2); 3 -> value.text(3); 4 -> value.text(1); else -> "" },
            subtitle = value.text(if (kind == 1) 3 else 4),
            enabled = kind == 1 && value.number(1) != 0L,
            summary = if (kind == 3) value.text(5).ifBlank { childSummary } else "",
            pageType = if (kind == 3 && redirectPage.number(4) > 0) redirectPage.number(4).toInt() else null,
            url = if (kind == 3) ImFields(value.bytes(2)).text(1).ifBlank { redirectPage.text(3) }.ifBlank { ImFields(value.bytes(6)).text(3) } else "",
            options = options,
        )
    }
    private fun parseMenu(bytes: ByteArray): ImMenuItem {
        val fields = ImFields(bytes)
        return ImMenuItem(fields.text(1), fields.number(4).toInt(), fields.text(3), fields.text(2))
    }
    private fun parseSession(bytes: ByteArray): ImSession? {
        val fields = ImFields(bytes)
        val id = fields.bytes(1)
        if (id.isEmpty()) return null
        val identity = ImFields(id)
        val info = ImFields(fields.bytes(2))
        val privateId = ImFields(identity.bytes(1)).number(1)
        val groupId = ImFields(identity.bytes(2)).number(1)
        val foldType = ImFields(identity.bytes(3)).number(1).toInt()
        val folder = when (foldType) { 2, 3 -> 5; 4 -> 2; 5 -> 3; 6 -> 4; 7, 10 -> 8; 9 -> 7; else -> null }
        val key = java.util.Base64.getEncoder().encodeToString(id)
        val unread = ImFields(fields.bytes(3))
        val summary = ImFields(fields.bytes(4))
        val timestamp = fields.number(5).let { if (it > 10_000_000_000L) it / 1_000_000 else it }
        val name = info.text(1)
        val avatar = parseAvatar(info.bytes(3))
        // vip_info 是服务端下发的 JSON 字符串；status>0 且 type==2 才是大会员名色。
        val vip = runCatching { Json.parseToJsonElement(info.text(4)).jsonObject }.getOrNull()
        val vipColor = (vip?.get("status")?.jsonPrimitive?.longOrNull ?: 0L) > 0L && vip?.get("type")?.jsonPrimitive?.longOrNull == 2L
        val userLabel = ImFields(ImFields(info.bytes(5)).bytes(2)).text(2)
        val text = listOf(summary.text(3), summary.text(1)).filter { it.isNotBlank() }.joinToString(" ")
        return ImSession(
            key = key, id = id, folderPage = folder, chatUrl = fields.text(9), summary = text,
            unreadLabel = if (unread.number(1) == 2L && unread.number(2) > 0L) unread.text(3) else "", unreadDot = unread.number(1) == 1L,
            isPrivate = privateId > 0L,
            systemNotice = ImFields(identity.bytes(4)).number(1) == 8L,
            profileMid = avatar.mid,
            pendantUrl = avatar.pendantUrl, officialType = avatar.officialType, vipColor = vipColor, userLabel = userLabel,
            item = SessionItem(
                identity_key = key,
                talker_id = privateId.takeIf { it > 0 } ?: groupId.takeIf { it > 0 } ?: -(key.hashCode().toLong().and(0xffffffffL) + 1L),
                session_type = if (privateId > 0) 1 else if (groupId > 0) 2 else -(identity.fields.firstOrNull()?.number ?: 0),
                top_ts = if (fields.number(6) != 0L) 1L else 0L,
                session_ts = timestamp * 1_000_000L,
                unread_count = unread.number(2).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                is_dnd = if (fields.number(8) != 0L) 1 else 0,
                max_seqno = fields.number(7),
                live_status = if (info.number(6) != 0L) 1 else 0,
                account_info = SessionAccountInfo(name = name, pic_url = avatar.url),
                group_name = name, group_cover = avatar.url,
                last_msg = SessionMessage(timestamp = timestamp, content = buildJsonObject { put("content", text) }.toString()),
            ),
        )
    }

    private fun layerResourceUrl(layerBytes: ByteArray): String {
        val resource = ImFields(ImFields(layerBytes).bytes(5))
        return resource.fields.filter { it.number in 2..4 && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }.firstNotNullOfOrNull { variant ->
            // 图片、动图和原生资源都通过 ResourceSource.remote 提供远程地址。
            val source = ImFields(ImFields(variant.bytes).bytes(1))
            ImFields(source.bytes(3)).text(1).takeIf { it.startsWith("http") }
        }.orEmpty()
    }

    private class ImAvatar(val url: String, val pendantUrl: String, val officialType: Int?, val mid: Long)

    /**
     * 会话头像分层：fallback_layers 里第 0 层是头像本体，第 1 层是挂件（图片或 WebP），
     * 最后一层若是认证图形则以 ResourceSource.local_value 承载（3 个人认证、4 机构认证）。
     */
    private fun parseAvatar(bytes: ByteArray): ImAvatar {
        val avatar = ImFields(bytes)
        val layers = ImFields(avatar.bytes(3)).all(2)
        val url = layers.firstOrNull()?.let { layerResourceUrl(it.bytes) }.orEmpty()
        val pendantUrl = layers.getOrNull(1)?.let { layerResourceUrl(it.bytes) }.orEmpty()
        val officialType = layers.lastOrNull()?.let { layer ->
            val image = ImFields(ImFields(layer.bytes).bytes(5)).bytes(2)
            when (ImFields(image).bytes(1).let { ImFields(it).number(4) }) { 3L -> 0; 4L -> 1; else -> null }
        }
        return ImAvatar(url, pendantUrl, officialType, avatar.number(4))
    }
}

internal class ImFields(bytes: ByteArray) {
    val fields = ProtoWire.parseFields(bytes)
    fun all(number: Int) = fields.filter { it.number == number }
    fun bytes(number: Int) = fields.lastOrNull { it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }?.bytes ?: byteArrayOf()
    fun text(number: Int) = fields.lastOrNull { it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }?.let(ProtoWire::stringValue).orEmpty()
    fun number(number: Int) = fields.lastOrNull { it.number == number && it.wireType == ProtoWire.WIRE_VARINT }?.varint ?: 0L
    fun replace(number: Int, value: ByteArray, nested: Boolean = true): ByteArray = ProtoWire.message(
        *fields.filter { it.number != number }.map(::encodeField).toTypedArray(),
        if (nested) ProtoWire.bytes(number, value) else value,
    )
    private fun encodeField(field: ProtoWire.Field): ByteArray = when (field.wireType) {
        ProtoWire.WIRE_VARINT -> ProtoWire.int64(field.number, field.varint)
        ProtoWire.WIRE_LENGTH_DELIMITED -> ProtoWire.bytes(field.number, field.bytes)
        ProtoWire.WIRE_FIXED64 -> ProtoWire.fixed64(field.number, field.fixed64)
        ProtoWire.WIRE_FIXED32 -> ProtoWire.fixed32(field.number, field.fixed32)
        else -> error("不支持的消息设置字段类型 ${field.wireType}")
    }
}
