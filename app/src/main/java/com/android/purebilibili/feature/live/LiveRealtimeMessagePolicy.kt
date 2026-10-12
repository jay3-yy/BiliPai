package com.android.purebilibili.feature.live

import com.android.purebilibili.data.model.response.LivePlayUrlData
import com.android.purebilibili.data.model.response.Playurl
import com.android.purebilibili.data.model.response.PlayurlInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt
import com.android.purebilibili.data.repository.LiveVoteInfo
import com.android.purebilibili.data.repository.LiveVoteOption

private val liveRealtimeJson = Json {
    ignoreUnknownKeys = true
}

internal sealed interface LiveRealtimeAction {
    data object Ignore : LiveRealtimeAction
    data class RefreshPlayback(val playUrlData: LivePlayUrlData? = null) : LiveRealtimeAction
    data class RoomUnavailable(val liveStatus: Int, val message: String) : LiveRealtimeAction
    data class RoomBlocked(val message: String) : LiveRealtimeAction
    data class UpdateWatchedText(val text: String) : LiveRealtimeAction
    data class UpdateOnlineRankCount(val count: Long) : LiveRealtimeAction
    data class UpdateRoomTitle(val title: String) : LiveRealtimeAction
    data class EmitChat(val item: LiveDanmakuItem) : LiveRealtimeAction
    data class EmitSuperChat(val item: LiveDanmakuItem, val id: Long) : LiveRealtimeAction
    data class RemoveSuperChats(val ids: List<Long>) : LiveRealtimeAction
    data class RecallDanmaku(val id: String) : LiveRealtimeAction
    data class RefreshRedPocket(val message: String) : LiveRealtimeAction
    data class UpdateVote(val vote: LiveVoteInfo, val announcement: LiveDanmakuItem) : LiveRealtimeAction
}

internal fun resolveLiveRealtimeAction(
    json: JsonObject,
    myMid: Long = 0L,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
): LiveRealtimeAction {
    val cmd = json.string("cmd")
    return when {
        cmd.startsWith("DANMU_MSG") -> parseLiveDanmakuMessage(json, myMid)
        cmd == "SUPER_CHAT_MESSAGE" || cmd == "SUPER_CHAT_MESSAGE_JPN" -> parseLiveSuperChat(json, nowEpochSeconds)
        cmd == "SUPER_CHAT_MESSAGE_DELETE" -> parseSuperChatDelete(json)
        cmd == "WATCHED_CHANGE" -> {
            val data = json.obj("data")
            val text = data?.string("text_large").orEmpty()
                .ifBlank { data?.string("text_small").orEmpty() }
            if (text.isBlank()) LiveRealtimeAction.Ignore else LiveRealtimeAction.UpdateWatchedText(text)
        }
        cmd == "ONLINE_RANK_COUNT" -> {
            val count = json.obj("data")?.long("count") ?: 0L
            if (count <= 0L) LiveRealtimeAction.Ignore else LiveRealtimeAction.UpdateOnlineRankCount(count)
        }
        cmd == "ROOM_CHANGE" || cmd == "ROOM_REAL_TIME_MESSAGE_UPDATE" -> {
            val title = json.obj("data")?.string("title").orEmpty()
            if (title.isBlank()) LiveRealtimeAction.Ignore else LiveRealtimeAction.UpdateRoomTitle(title)
        }
        cmd == "PLAYURL_RELOAD" || cmd == "LIVE" -> LiveRealtimeAction.RefreshPlayback(parseReloadPlayUrl(json))
        cmd == "PREPARING" -> LiveRealtimeAction.RoomUnavailable(0, "主播暂未开播")
        cmd == "CUT_OFF" || cmd == "CUT_OFF_V2" || cmd == "WARNING" -> {
            LiveRealtimeAction.RoomBlocked(resolveBlockingMessage(json, cmd))
        }
        cmd == "SEND_GIFT" || cmd == "COMBO_SEND" || cmd == "SPECIAL_GIFT" -> parseGiftMessage(json, cmd)
        cmd == "GUARD_BUY" || cmd == "USER_TOAST_MSG" -> parseGuardMessage(json, cmd)
        cmd == "INTERACT_WORD" || cmd == "INTERACT_WORD_V2" -> parseInteractMessage(json)
        cmd == "DM_INTERACTION" -> parseDmInteraction(json)
        cmd == "RECALL_DANMU_MSG" -> {
            val id = json.obj("data")?.string("target_id").orEmpty()
            if (id.isBlank()) LiveRealtimeAction.Ignore else LiveRealtimeAction.RecallDanmaku(id)
        }
        cmd == "ROOM_SILENT_ON" -> systemMessage("系统", "直播间已开启等级禁言")
        cmd == "ROOM_SILENT_OFF" -> systemMessage("系统", "直播间已关闭等级禁言")
        cmd == "ROOM_BLOCK_MSG" -> systemMessage("系统", "有用户被禁言")
        cmd.startsWith("POPULARITY_RED_POCKET") -> {
            LiveRealtimeAction.RefreshRedPocket("直播间红包状态更新")
        }
        cmd.startsWith("ANCHOR_LOT") -> systemMessage("天选", "天选时刻状态更新")
        else -> LiveRealtimeAction.Ignore
    }
}

private fun parseLiveDanmakuMessage(json: JsonObject, myMid: Long): LiveRealtimeAction {
    val info = json.array("info") ?: return LiveRealtimeAction.Ignore
    if (info.size < 3) return LiveRealtimeAction.Ignore
    val meta = info.array(0) ?: return LiveRealtimeAction.Ignore
    val text = info.string(1)
    val user = info.array(2) ?: return LiveRealtimeAction.Ignore
    if (text.isBlank()) return LiveRealtimeAction.Ignore

    val extraPayload = meta.obj(15)
        ?.string("extra")
        ?.takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { Json.parseToJsonElement(raw).asObjectOrNull() }.getOrNull() }
    val checkInfo = info.obj(9)
    val medalArray = info.array(3)
    val levelArray = info.array(4)
    val uid = user.long(0)

    return LiveRealtimeAction.EmitChat(
        LiveDanmakuItem(
            text = text,
            color = meta.int(3, 16777215),
            mode = meta.int(1, 1),
            uid = uid,
            uname = user.string(1),
            isSelf = uid > 0L && uid == myMid,
            emoticonUrl = resolveDanmakuEmoticonUrl(text, meta, extraPayload),
            medalLevel = medalArray?.int(0) ?: 0,
            medalName = medalArray?.string(1).orEmpty(),
            medalColor = medalArray?.int(4) ?: 0,
            userLevel = levelArray?.int(0) ?: 0,
            isAdmin = user.int(2) == 1,
            guardLevel = info.int(7),
            replyToName = extraPayload?.string("reply_uname").orEmpty(),
            replyToUid = extraPayload?.long("reply_mid") ?: 0L,
            dmType = extraPayload?.int("dm_type") ?: 0,
            idStr = extraPayload?.string("id_str").orEmpty(),
            reportTs = checkInfo?.long("ts") ?: 0L,
            reportSign = checkInfo?.string("ct").orEmpty()
        )
    )
}

private fun parseLiveSuperChat(json: JsonObject, nowEpochSeconds: Long): LiveRealtimeAction {
    val data = json.obj("data") ?: return LiveRealtimeAction.Ignore
    val userInfo = data.obj("user_info")
    val uid = data.long("uid").takeIf { it > 0L } ?: userInfo?.long("uid") ?: 0L
    val uname = userInfo?.string("uname").orEmpty()
        .ifBlank { userInfo?.string("name").orEmpty() }
        .ifBlank { "醒目留言" }
    val message = data.string("message")
    if (message.isBlank()) return LiveRealtimeAction.Ignore
    val id = data.long("id").takeIf { it > 0L } ?: data.long("message_id")
    val duration = data.int("time").takeIf { it > 0 } ?: data.int("duration")
    val item = LiveDanmakuItem(
        text = message,
        uid = uid,
        uname = uname,
        isSuperChat = true,
        superChatId = id,
        superChatPrice = data.int("price").takeIf { it > 0 }?.let { "¥$it" }.orEmpty(),
        superChatBackgroundColor = parseLiveRealtimeColor(
            data["background_bottom_color"] ?: data["background_color"]
        ),
        superChatToken = data.string("token"),
        superChatReportTs = data.long("ts").takeIf { it > 0L } ?: data.long("start_time"),
        superChatDuration = duration.coerceAtLeast(0),
        superChatEndTime = resolveLiveSuperChatEndTime(
            endTime = data.long("end_time"),
            startTime = data.long("start_time"),
            duration = duration,
            nowEpochSeconds = nowEpochSeconds,
        ),
    )
    return LiveRealtimeAction.EmitSuperChat(item, id)
}

private fun parseReloadPlayUrl(json: JsonObject): LivePlayUrlData? {
    val playurl = json.obj("data")?.obj("playurl") ?: return null
    val parsedPlayurl = runCatching {
        liveRealtimeJson.decodeFromJsonElement<Playurl>(playurl)
    }.getOrNull() ?: return null
    return LivePlayUrlData(
        playurl_info = PlayurlInfo(playurl = parsedPlayurl),
        quality_description = parsedPlayurl.gQnDesc,
        current_quality = parsedPlayurl.stream
            ?.firstOrNull()
            ?.format
            ?.firstOrNull()
            ?.codec
            ?.firstOrNull()
            ?.currentQn
            ?: 0
    )
}

private fun parseSuperChatDelete(json: JsonObject): LiveRealtimeAction {
    val data = json.obj("data") ?: return LiveRealtimeAction.Ignore
    val ids = (data.array("ids") ?: data.array("message_ids"))
        ?.toLongList()
        .orEmpty()
    return if (ids.isEmpty()) LiveRealtimeAction.Ignore else LiveRealtimeAction.RemoveSuperChats(ids)
}

private fun parseGiftMessage(json: JsonObject, cmd: String): LiveRealtimeAction {
    val data = json.obj("data") ?: return LiveRealtimeAction.Ignore
    val uname = data.string("uname").ifBlank { data.string("username") }.ifBlank { "有人" }
    val giftName = data.string("giftName")
        .ifBlank { data.string("gift_name") }
        .ifBlank { if (cmd == "SPECIAL_GIFT") "特殊礼物" else "礼物" }
    val count = (data.int("num").takeIf { it > 0 } ?: data.int("total_num").takeIf { it > 0 } ?: 1)
    return systemMessage("礼物", "$uname 赠送 $giftName x$count")
}

private fun parseGuardMessage(json: JsonObject, cmd: String): LiveRealtimeAction {
    val data = json.obj("data") ?: return LiveRealtimeAction.Ignore
    val uname = data.string("username").ifBlank { data.string("uname") }.ifBlank { "有人" }
    val guardLevel = data.int("guard_level").takeIf { it > 0 } ?: data.int("guardLevel")
    val guardName = when (guardLevel) {
        1 -> "总督"
        2 -> "提督"
        3 -> "舰长"
        else -> data.string("role_name").ifBlank { "大航海" }
    }
    val count = data.int("num", 1).coerceAtLeast(1)
    val suffix = if (count > 1) " x$count" else ""
    val verb = if (cmd == "USER_TOAST_MSG") "续费" else "开通"
    return systemMessage("舰队", "$uname $verb $guardName$suffix")
}

private fun parseInteractMessage(json: JsonObject): LiveRealtimeAction {
    val data = json.obj("data") ?: return LiveRealtimeAction.Ignore
    val uname = data.string("uname").ifBlank { data.string("username") }
    if (uname.isBlank()) return LiveRealtimeAction.Ignore
    val message = when (data.int("msg_type")) {
        2 -> "$uname 关注了主播"
        3 -> "$uname 分享了直播间"
        else -> "$uname 进入直播间"
    }
    return systemMessage("互动", message, uid = data.long("uid"))
}

private fun parseDmInteraction(json: JsonObject): LiveRealtimeAction {
    val data = json.obj("data") ?: return LiveRealtimeAction.Ignore
    val nested = data.string("data")
        .takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { Json.parseToJsonElement(raw).asObjectOrNull() }.getOrNull() }
        ?: return LiveRealtimeAction.Ignore
    return when (data.int("type")) {
        101 -> parseVoteInteraction(nested)
        103 -> parseFollowInteraction(nested)
        else -> LiveRealtimeAction.Ignore
    }
}

private fun parseVoteInteraction(data: JsonObject): LiveRealtimeAction {
    val question = data.string("question").ifBlank { return LiveRealtimeAction.Ignore }
    val voteOptions = data.array("options").orEmpty()
        .mapNotNull { option ->
            val obj = option.asObjectOrNull() ?: return@mapNotNull null
            val desc = obj.string("desc").ifBlank { return@mapNotNull null }
            LiveVoteOption(
                id = obj.int("idx"),
                description = desc,
                percent = obj.float("percent").coerceIn(0f, 1f)
            )
        }
        .take(2)
    if (voteOptions.isEmpty()) return LiveRealtimeAction.Ignore
    val remainingSeconds = (data.long("left_duration") / 1000L).coerceAtLeast(0L)
    val suffix = if (remainingSeconds > 0L) "｜剩余 ${remainingSeconds}s" else ""
    val optionSummary = voteOptions.joinToString(" / ") {
        "${it.description} ${(it.percent * 100f).roundToInt()}%"
    }
    val announcement = systemMessage("投票", "投票：$question｜$optionSummary$suffix")
    val chat = (announcement as? LiveRealtimeAction.EmitChat)?.item ?: return LiveRealtimeAction.Ignore
    return LiveRealtimeAction.UpdateVote(
        vote = LiveVoteInfo(
            status = data.int("status", 4),
            question = question,
            options = voteOptions,
            durationMillis = data.long("duration"),
            remainingMillis = data.long("left_duration"),
            resultText = data.string("result_text"),
            endTimeText = data.string("etime_str"),
            interactionId = data.long("interaction_id")
        ),
        announcement = chat
    )
}

private fun parseFollowInteraction(data: JsonObject): LiveRealtimeAction {
    val count = data.long("cnt").takeIf { it > 0L } ?: return LiveRealtimeAction.Ignore
    return systemMessage("关注", "已有 $count 人关注了主播")
}

private fun resolveDanmakuEmoticonUrl(
    text: String,
    meta: JsonArray,
    extraPayload: JsonObject?
): String? {
    val directUrl = meta.obj(13)?.string("url").orEmpty()
    if (directUrl.isNotBlank()) return directUrl
    val emots = extraPayload?.obj("emots") ?: return null
    val exact = emots.obj(text)?.string("url").orEmpty()
    if (exact.isNotBlank()) return exact
    return null
}

private fun resolveBlockingMessage(json: JsonObject, cmd: String): String {
    val data = json.obj("data")
    return json.string("msg")
        .ifBlank { json.string("message") }
        .ifBlank { data?.string("msg").orEmpty() }
        .ifBlank {
            when (cmd) {
                "WARNING" -> "直播间收到警告"
                else -> "直播间已被切断"
            }
        }
}

private fun systemMessage(uname: String, text: String, uid: Long = 0L): LiveRealtimeAction {
    return LiveRealtimeAction.EmitChat(
        LiveDanmakuItem(
            text = text,
            uid = uid,
            uname = uname,
            isSystem = true,
            color = 0xFFB54A,
            mode = 1
        )
    )
}

private fun JsonObject.string(name: String): String {
    return this[name]?.asPrimitiveOrNull()?.contentOrNull.orEmpty()
}

private fun JsonObject.int(name: String, default: Int = 0): Int {
    return this[name]?.asPrimitiveOrNull()?.intOrNull ?: default
}

private fun JsonObject.long(name: String): Long {
    return this[name]?.asPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0L
}

private fun JsonObject.float(name: String): Float {
    return this[name]?.asPrimitiveOrNull()?.contentOrNull?.toFloatOrNull() ?: 0f
}

private fun JsonObject.obj(name: String): JsonObject? = this[name]?.asObjectOrNull()

private fun JsonObject.array(name: String): JsonArray? = this[name]?.asArrayOrNull()

private fun JsonArray.string(index: Int): String {
    return getOrNull(index)?.asPrimitiveOrNull()?.contentOrNull.orEmpty()
}

private fun JsonArray.int(index: Int, default: Int = 0): Int {
    return getOrNull(index)?.asPrimitiveOrNull()?.intOrNull ?: default
}

private fun JsonArray.long(index: Int): Long {
    return getOrNull(index)?.asPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0L
}

private fun JsonArray.obj(index: Int): JsonObject? = getOrNull(index)?.asObjectOrNull()

private fun JsonArray.array(index: Int): JsonArray? = getOrNull(index)?.asArrayOrNull()

private fun JsonArray.toLongList(): List<Long> {
    return mapNotNull { it.asPrimitiveOrNull()?.contentOrNull?.toLongOrNull()?.takeIf { id -> id > 0L } }
}

private fun parseLiveRealtimeColor(value: JsonElement?): Int {
    val primitive = value?.asPrimitiveOrNull() ?: return 0
    val raw = primitive.contentOrNull ?: return 0
    if (raw.startsWith("#")) {
        val hex = raw.removePrefix("#")
        val color = hex.toLongOrNull(16)?.toInt() ?: return 0
        return when (hex.length) {
            6 -> color or 0xFF000000.toInt()
            8 -> color
            else -> 0
        }
    }
    return primitive.intOrNull
        ?: raw.toLongOrNull(16)?.toInt()
        ?: 0
}

private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement.asPrimitiveOrNull(): JsonPrimitive? {
    if (this is JsonNull) return null
    return this as? JsonPrimitive
}
