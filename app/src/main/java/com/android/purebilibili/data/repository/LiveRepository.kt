// 文件路径: data/repository/LiveRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.feature.live.LiveMixedPlaybackPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import java.util.Base64

data class LiveRoomH5Snapshot(
    val roomId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val appBackground: String = "",
    val watchedText: String = "",
    val anchorName: String = "",
    val anchorFace: String = "",
    val liveStartTime: Long = 0,
    val online: Int = 0
)

data class LivePrefetchDanmaku(
    val uid: Long = 0,
    val uname: String = "",
    val text: String = "",
    val emoticonUrl: String? = null,
    val replyToName: String = "",
    val dmType: Int = 0,
    val idStr: String = "",
    val reportTs: Long = 0,
    val reportSign: String = ""
)

data class LiveSuperChatSeed(
    val id: Long = 0,
    val uid: Long = 0,
    val uname: String = "",
    val message: String = "",
    val price: String = "",
    val backgroundColor: Int = 0,
    val token: String = "",
    val reportTs: Long = 0
)

data class LiveRedPocketInfo(
    val lotId: Long = 0,
    val senderName: String = "",
    val danmu: String = "",
    val h5Url: String = "",
    val awardsText: String = "",
    val totalPrice: Int = 0,
    val userStatus: Int = 0,
    val remainingSeconds: Int = 0
)

data class LiveAreaRoomsPage(
    val rooms: List<LiveRoom>,
    val hasMore: Boolean,
    val totalCount: Int = 0
)

data class LiveHeartbeatSession(
    val roomId: Long,
    val nextIntervalSec: Int = DEFAULT_LIVE_HEARTBEAT_INTERVAL_SEC
)

data class LiveDanmakuColorOption(
    val name: String,
    val color: Int,
    val colorHex: String
)

data class LiveDanmakuModeOption(
    val name: String,
    val mode: Int
)

data class LiveDanmakuPermission(
    val canSend: Boolean = true,
    val statusText: String = "可发送弹幕",
    val maxLength: Int = DEFAULT_LIVE_DANMAKU_MAX_LENGTH,
    val availableColors: List<LiveDanmakuColorOption> = emptyList(),
    val availableModes: List<LiveDanmakuModeOption> = emptyList()
)

private const val DEFAULT_LIVE_HEARTBEAT_INTERVAL_SEC = 60
private const val DEFAULT_LIVE_DANMAKU_MAX_LENGTH = 40

internal fun parseLiveDanmakuHistoryItems(rawJson: String): Result<List<LivePrefetchDanmaku>> {
    val root = liveRepositoryJson.parseToJsonElement(rawJson).jsonObject
    if (root.int("code", -1) != 0) {
        return Result.failure(Exception(root.string("message").ifBlank { "获取直播弹幕历史失败" }))
    }
    val roomArray = root.obj("data")?.array("room")
    val items = buildList {
        roomArray?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val user = obj.obj("user")
            val base = user?.obj("base")
            val checkInfo = obj.obj("check_info")
            val reply = obj.obj("reply")
            add(
                LivePrefetchDanmaku(
                    uid = user?.long("uid") ?: 0L,
                    uname = base?.string("name").orEmpty(),
                    text = obj.string("text"),
                    emoticonUrl = obj.obj("emoticon")
                        ?.string("url")
                        ?.takeIf { it.isNotBlank() },
                    replyToName = reply?.string("reply_uname").orEmpty(),
                    dmType = obj.int("dm_type"),
                    idStr = obj.string("id_str"),
                    reportTs = checkInfo?.long("ts") ?: 0L,
                    reportSign = checkInfo?.string("ct").orEmpty()
                )
            )
        }
    }
    return Result.success(items)
}

internal fun buildLiveHeartbeatQuery(
    roomId: Long,
    lastIntervalSec: Int = DEFAULT_LIVE_HEARTBEAT_INTERVAL_SEC
): Map<String, String> {
    val payload = "${lastIntervalSec.coerceAtLeast(1)}|${roomId.coerceAtLeast(0L)}|1|0"
    return mapOf(
        "hb" to Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8)),
        "pf" to "web"
    )
}

internal fun parseLiveHeartbeatNextInterval(rawJson: String): Int {
    val root = runCatching {
        liveRepositoryJson.parseToJsonElement(rawJson).jsonObject
    }.getOrNull() ?: return DEFAULT_LIVE_HEARTBEAT_INTERVAL_SEC
    if (root.int("code", -1) != 0) return DEFAULT_LIVE_HEARTBEAT_INTERVAL_SEC
    return root.obj("data")
        ?.int("next_interval")
        ?.takeIf { it > 0 }
        ?: DEFAULT_LIVE_HEARTBEAT_INTERVAL_SEC
}

internal fun parseLiveDanmakuPermission(rawJson: String): LiveDanmakuPermission {
    val root = runCatching {
        liveRepositoryJson.parseToJsonElement(rawJson).jsonObject
    }.getOrNull() ?: return LiveDanmakuPermission(
        canSend = false,
        statusText = "弹幕配置解析失败",
        maxLength = 0
    )
    if (root.int("code", -1) != 0) {
        val message = root.string("message").ifBlank { root.string("msg") }.ifBlank { "弹幕配置获取失败" }
        return LiveDanmakuPermission(canSend = false, statusText = message, maxLength = 0)
    }
    val data = root.obj("data")
    val colors = data
        ?.array("group")
        ?.flatMap { group ->
            (group as? JsonObject)
                ?.array("color")
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
        }
        ?.filter { it.int("status") == 1 }
        ?.map { color ->
            LiveDanmakuColorOption(
                name = color.string("name"),
                color = color.string("color").toIntOrNull() ?: 16777215,
                colorHex = color.string("color_hex")
            )
        }
        .orEmpty()
    val modes = data
        ?.array("mode")
        ?.mapNotNull { it as? JsonObject }
        ?.filter { it.int("status") == 1 }
        ?.map { mode ->
            LiveDanmakuModeOption(
                name = mode.string("name"),
                mode = mode.int("mode")
            )
        }
        .orEmpty()
    val canSend = colors.isNotEmpty() && modes.isNotEmpty()
    return LiveDanmakuPermission(
        canSend = canSend,
        statusText = if (canSend) "可发送弹幕" else "当前账号暂无可用弹幕样式",
        maxLength = if (canSend) DEFAULT_LIVE_DANMAKU_MAX_LENGTH else 0,
        availableColors = colors,
        availableModes = modes
    )
}

internal fun parseLiveShieldInfo(rawJson: String): Result<LiveShieldInfo> {
    return runCatching {
        val root = JSONObject(rawJson)
        if (root.optInt("code", -1) != 0) {
            return Result.failure(Exception(root.optString("message", "获取直播屏蔽设置失败")))
        }
        val data = root.optJSONObject("data") ?: JSONObject()
        val shieldInfo = data.optJSONObject("shield_info")
            ?: data.optJSONObject("silent_info")
            ?: data
        val rules = shieldInfo.optJSONObject("shield_rule")
            ?: shieldInfo.optJSONObject("shield_rules")
            ?: shieldInfo
        LiveShieldInfo(
            level = firstPositiveInt(rules, "level"),
            medal = firstPositiveInt(rules, "medal", "medal_level"),
            verify = firstPositiveInt(rules, "verify", "verify_level", "shield_verify"),
            rank = firstPositiveInt(rules, "rank"),
            phone = firstPositiveInt(rules, "phone"),
            keywords = parseLiveShieldKeywords(
                shieldInfo.optJSONArray("keyword_list")
                    ?: shieldInfo.optJSONArray("keywords")
                    ?: data.optJSONArray("keyword_list")
            ),
            users = parseLiveShieldUsers(
                shieldInfo.optJSONArray("user_list")
                    ?: shieldInfo.optJSONArray("shield_user_list")
                    ?: data.optJSONArray("user_list")
            )
        )
    }
}

private fun parseLiveShieldKeywords(array: org.json.JSONArray?): List<LiveShieldKeyword> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val raw = array.opt(index)
            when (raw) {
                is String -> add(LiveShieldKeyword(keyword = raw))
                is JSONObject -> {
                    val keyword = raw.optString("keyword")
                        .ifBlank { raw.optString("content") }
                        .ifBlank { raw.optString("msg") }
                    if (keyword.isNotBlank()) {
                        add(
                            LiveShieldKeyword(
                                id = raw.optLong("id", 0L),
                                keyword = keyword
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun parseLiveShieldUsers(array: org.json.JSONArray?): List<LiveShieldUser> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val raw = array.optJSONObject(index) ?: continue
            val uid = firstPositiveLong(raw, "uid", "mid", "tuid")
            if (uid > 0L) {
                add(
                    LiveShieldUser(
                        uid = uid,
                        uname = raw.optString("uname").ifBlank { raw.optString("name") },
                        face = raw.optString("face"),
                        id = raw.optLong("id", 0L)
                    )
                )
            }
        }
    }
}

private fun firstPositiveInt(json: JSONObject, vararg names: String): Int {
    return names.firstNotNullOfOrNull { name ->
        json.optInt(name, 0).takeIf { it > 0 }
    } ?: 0
}

private fun firstPositiveLong(json: JSONObject, vararg names: String): Long {
    return names.firstNotNullOfOrNull { name ->
        json.optLong(name, 0L).takeIf { it > 0L }
    } ?: 0L
}

enum class LiveContributionRankType(
    val title: String,
    val switchValue: String
) {
    ONLINE("在线榜", "contribution_rank"),
    DAILY("日榜", "today_rank"),
    WEEKLY("周榜", "current_week_rank"),
    MONTHLY("月榜", "current_month_rank")
}

private val liveRepositoryJson = Json {
    ignoreUnknownKeys = true
}

internal fun parseLiveRedPocketInfo(rawJson: String): LiveRedPocketInfo? {
    val root = runCatching {
        liveRepositoryJson.parseToJsonElement(rawJson).jsonObject
    }.getOrNull() ?: return null
    if (root.int("code", -1) != 0) return null
    val pocket = root.obj("data")
        ?.array("popularity_red_pocket")
        ?.firstNotNullOfOrNull { it as? JsonObject }
        ?: return null
    val lotId = pocket.long("lot_id")
    if (lotId <= 0L) return null
    val currentTime = pocket.long("current_time")
    val endTime = pocket.long("end_time")
    val remainingSeconds = (endTime - currentTime)
        .takeIf { it > 0L }
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: 0
    return LiveRedPocketInfo(
        lotId = lotId,
        senderName = pocket.string("sender_name"),
        danmu = pocket.string("danmu"),
        h5Url = pocket.string("h5_url"),
        awardsText = formatLiveRedPocketAwards(pocket.array("awards")),
        totalPrice = pocket.int("total_price"),
        userStatus = pocket.int("user_status"),
        remainingSeconds = remainingSeconds
    )
}

private fun formatLiveRedPocketAwards(awards: JsonArray?): String {
    return awards
        ?.mapNotNull { it as? JsonObject }
        ?.mapNotNull { award ->
            val name = award.string("gift_name")
            if (name.isBlank()) return@mapNotNull null
            val count = award.int("num").takeIf { it > 0 } ?: 1
            "$name x$count"
        }
        ?.take(3)
        ?.joinToString("、")
        .orEmpty()
}

private fun JsonObject.string(name: String): String {
    return this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.int(name: String, default: Int = 0): Int {
    return this[name]?.jsonPrimitive?.intOrNull ?: default
}

private fun JsonObject.long(name: String): Long {
    return this[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
}

private fun JsonObject.obj(name: String): JsonObject? {
    return this[name] as? JsonObject
}

private fun JsonObject.array(name: String): JsonArray? {
    return this[name] as? JsonArray
}

/**
 * 直播相关数据仓库
 * 从 VideoRepository 拆分出来，专注于直播功能
 */
object LiveRepository {
    private val api = NetworkModule.api

    private suspend fun resolveRealRoomId(roomId: Long): Long {
        return try {
            val resp = api.getLiveRoomInit(roomId)
            resp.data?.roomId?.takeIf { it > 0L } ?: roomId
        } catch (_: Exception) {
            roomId
        }
    }

    /**
     * 获取热门直播列表
     */
    suspend fun getLiveRooms(page: Int = 1): Result<List<LiveRoom>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getLiveList(page = page)
            // 使用 getAllRooms() 兼容新旧 API 格式
            val list = resp.data?.getAllRooms() ?: emptyList()
            list.firstOrNull()?.let {
                com.android.purebilibili.core.util.Logger.d("LiveRepo", "🟢 Popular Live: roomid=${it.roomid}, title=${it.title}, online=${it.online}")
            }
            com.android.purebilibili.core.util.Logger.d("LiveRepo", "🔴 getLiveRooms page=$page, count=${list.size}")
            Result.success(list)
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e("LiveRepo", " getLiveRooms failed", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getRecommendedLiveRooms(): Result<List<LiveRoom>> = withContext(Dispatchers.IO) {
        try {
            val recommendResp = api.getLiveRecommendList()
            val recommendRooms = if (recommendResp.code == 0) {
                recommendResp.data?.recommendRoomList
                    ?.filterNot { it.isAd }
                    ?.map { it.toLiveRoom() }
                    .orEmpty()
            } else {
                emptyList()
            }
            if (recommendRooms.isNotEmpty()) {
                Result.success(recommendRooms)
            } else {
                getLiveRooms(page = 1)
            }
        } catch (e: Exception) {
            getLiveRooms(page = 1)
        }
    }

    suspend fun getLiveAreaIndex(): Result<List<LiveAreaParent>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getLiveAreaList()
            if (resp.code == 0) {
                Result.success(resp.data ?: emptyList())
            } else {
                Result.failure(Exception(resp.message.ifBlank { resp.msg.ifBlank { "获取直播标签失败" } }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAreaRooms(
        parentAreaId: Int,
        areaId: Int = 0,
        page: Int = 1,
        sortType: String = "online",
        areaTitle: String = ""
    ): Result<List<LiveRoom>> = getAreaRoomsPage(
        parentAreaId = parentAreaId,
        areaId = areaId,
        page = page,
        sortType = sortType,
        areaTitle = areaTitle
    ).map { it.rooms }

    suspend fun getAreaRoomsPage(
        parentAreaId: Int,
        areaId: Int = 0,
        page: Int = 1,
        pageSize: Int = 30,
        sortType: String = "online",
        areaTitle: String = ""
    ): Result<LiveAreaRoomsPage> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getLiveList(
                parentAreaId = parentAreaId,
                areaId = areaId,
                page = page,
                pageSize = pageSize,
                sortType = sortType
            )
            if (resp.code == 0) {
                val data = resp.data
                val rooms = data?.getAllRooms() ?: emptyList()
                Result.success(
                    LiveAreaRoomsPage(
                        rooms = rooms,
                        hasMore = hasMoreLiveAreaRooms(
                            loadedCount = rooms.size,
                            page = page,
                            pageSize = pageSize,
                            hasMoreFlag = data?.hasMore ?: 0,
                            totalCount = data?.count ?: 0
                        ),
                        totalCount = data?.count ?: 0
                    )
                )
            } else if (shouldFallbackLiveAreaRooms(code = resp.code, message = resp.message)) {
                val fallbackResp = api.getLiveSecondAreaList(
                    parentAreaId = parentAreaId,
                    areaId = areaId,
                    page = page,
                    sortType = sortType
                )
                if (fallbackResp.code == 0) {
                    val data = fallbackResp.data
                    Result.success(
                        LiveAreaRoomsPage(
                            rooms = data?.list ?: emptyList(),
                            hasMore = data?.hasMore == 1,
                            totalCount = data?.count ?: 0
                        )
                    )
                } else {
                    Result.failure(
                        Exception(
                            resolveLiveAreaRoomsErrorMessage(
                                code = fallbackResp.code,
                                message = fallbackResp.message
                            )
                        )
                    )
                }
            } else {
                Result.failure(
                    Exception(
                        resolveLiveAreaRoomsErrorMessage(
                            code = resp.code,
                            message = resp.message
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取关注的直播间（需要登录）
     */
    suspend fun getFollowedLive(page: Int = 1): Result<List<LiveRoom>> = getFollowedLivePage(page).map { it.items }

    suspend fun getFollowedLivePage(
        page: Int = 1,
        pageSize: Int = 50
    ): Result<LivePagedResult<LiveRoom>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getFollowedLive(page = page, pageSize = pageSize)
            val followedRooms = resp.data?.list
                ?.filter { it.liveStatus == 1 }
                ?: emptyList()

            val liveRooms = followedRooms.map { it.toLiveRoom() }
            val pageInfo = resp.data?.pageinfo
            val hasMore = when {
                pageInfo != null && pageInfo.total_page > 0 -> page < pageInfo.total_page
                followedRooms.size >= pageSize -> true
                else -> false
            }

            Result.success(
                LivePagedResult(
                    items = liveRooms.distinctBy { it.roomid },
                    hasMore = hasMore,
                    nextPage = page + 1,
                    totalCount = resp.data?.livingNum ?: liveRooms.size
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getRoomH5Info(roomId: Long): Result<LiveRoomH5Snapshot> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val json = JSONObject(api.getLiveRoomH5Info(realRoomId).string())
            if (json.optInt("code", -1) != 0) {
                return@withContext Result.failure(Exception(json.optString("message", "获取直播间 H5 信息失败")))
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val room = data.optJSONObject("room_info") ?: JSONObject()
            val anchor = data.optJSONObject("anchor_info")?.optJSONObject("base_info") ?: JSONObject()
            val watched = data.optJSONObject("watched_show") ?: JSONObject()
            Result.success(
                LiveRoomH5Snapshot(
                    roomId = room.optLong("room_id", realRoomId),
                    title = room.optString("title"),
                    cover = room.optString("cover"),
                    appBackground = room.optString("app_background"),
                    watchedText = watched.optString("text_large").ifBlank { watched.optString("text_small") },
                    anchorName = anchor.optString("uname"),
                    anchorFace = anchor.optString("face"),
                    liveStartTime = room.optLong("live_start_time", 0L),
                    online = room.optInt("online", watched.optInt("num", 0))
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun signWithWbi(params: Map<String, String>): Map<String, String> {
        return try {
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            val imgKey = wbiImg?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            val subKey = wbiImg?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            if (imgKey.isNotEmpty() && subKey.isNotEmpty()) {
                com.android.purebilibili.core.network.WbiUtils.sign(params, imgKey, subKey)
            } else {
                params
            }
        } catch (_: Exception) {
            params
        }
    }

    suspend fun getLiveDanmakuHistory(roomId: Long): Result<List<LivePrefetchDanmaku>> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            parseLiveDanmakuHistoryItems(api.getLiveDanmakuHistory(realRoomId).string())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveDanmakuPermission(roomId: Long): Result<LiveDanmakuPermission> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            Result.success(parseLiveDanmakuPermission(api.getLiveDanmakuConfig(realRoomId).string()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportLiveHeartbeat(
        roomId: Long,
        lastIntervalSec: Int = DEFAULT_LIVE_HEARTBEAT_INTERVAL_SEC
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val raw = api.reportLiveHeartbeat(
                buildLiveHeartbeatQuery(
                    roomId = realRoomId,
                    lastIntervalSec = lastIntervalSec
                )
            ).string()
            Result.success(parseLiveHeartbeatNextInterval(raw))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveSuperChatMessages(roomId: Long): Result<List<LiveSuperChatSeed>> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val json = JSONObject(api.getLiveSuperChatMessages(realRoomId).string())
            if (json.optInt("code", -1) != 0) {
                return@withContext Result.failure(Exception(json.optString("message", "获取醒目留言失败")))
            }
            val list = json.optJSONObject("data")?.optJSONArray("list")
            val items = buildList {
                if (list != null) {
                    for (index in 0 until list.length()) {
                        val obj = list.optJSONObject(index) ?: continue
                        val user = obj.optJSONObject("user_info")
                        add(
                            LiveSuperChatSeed(
                                id = obj.optLong("id", obj.optLong("message_id", 0L)),
                                uid = obj.optLong("uid", user?.optLong("uid", 0L) ?: 0L),
                                uname = user?.optString("uname").orEmpty(),
                                message = obj.optString("message"),
                                price = obj.optInt("price", 0).takeIf { it > 0 }?.let { "¥$it" }.orEmpty(),
                                backgroundColor = parseLiveColorInt(
                                    obj.optString("background_bottom_color").ifBlank {
                                        obj.optString("background_color")
                                    }
                                ),
                                token = obj.optString("token"),
                                reportTs = obj.optLong("ts", obj.optLong("start_time", 0L))
                            )
                        )
                    }
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveRedPocketInfo(roomId: Long): Result<LiveRedPocketInfo?> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            Result.success(parseLiveRedPocketInfo(api.getLiveLotteryInfo(realRoomId).string()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveContributionRank(
        roomId: Long,
        ruid: Long,
        type: LiveContributionRankType,
        page: Int = 1
    ): Result<List<LiveContributionRankItem>> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val params = signWithWbi(
                mapOf(
                    "ruid" to ruid.toString(),
                    "room_id" to realRoomId.toString(),
                    "page" to page.toString(),
                    "page_size" to "100",
                    "type" to type.name.lowercase(),
                    "switch" to type.switchValue,
                    "platform" to "web",
                    "web_location" to "444.8"
                )
            )
            val resp = api.getLiveContributionRank(params)
            if (resp.code == 0) {
                Result.success(resp.data?.item ?: emptyList())
            } else {
                Result.failure(Exception(resp.message.ifBlank { "获取高能榜失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shieldLiveUser(
        roomId: Long,
        uid: Long,
        type: Int = 1
    ): Result<Boolean> = setLiveShieldUser(roomId, uid, type).map { true }

    suspend fun getLiveShieldInfo(roomId: Long): Result<LiveShieldInfo> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val params = signWithWbi(
                mapOf(
                    "room_id" to realRoomId.toString(),
                    "from" to "0",
                    "web_location" to "444.8"
                )
            )
            parseLiveShieldInfo(api.getLiveInfoByUser(params).string())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setLiveSilentRule(
        type: String,
        level: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isBlank()) return@withContext Result.failure(Exception("请先登录"))
            val resp = api.setLiveSilentRule(
                type = type,
                level = level,
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message.ifBlank { "屏蔽规则设置失败" }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addLiveShieldKeyword(keyword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isBlank()) return@withContext Result.failure(Exception("请先登录"))
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) return@withContext Result.failure(Exception("请输入屏蔽词"))
            val resp = api.addLiveShieldKeyword(
                keyword = trimmed,
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message.ifBlank { "添加屏蔽词失败" }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLiveShieldKeyword(keyword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isBlank()) return@withContext Result.failure(Exception("请先登录"))
            val resp = api.deleteLiveShieldKeyword(
                keyword = keyword,
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message.ifBlank { "删除屏蔽词失败" }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setLiveShieldUser(
        roomId: Long,
        uid: Long,
        type: Int = 1
    ): Result<LiveShieldUser> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isBlank()) return@withContext Result.failure(Exception("请先登录"))
            val resp = api.shieldLiveUser(
                uid = uid,
                roomId = realRoomId,
                type = type,
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) {
                Result.success(LiveShieldUser(uid = uid))
            } else {
                Result.failure(Exception(resp.message.ifBlank { if (type == 1) "直播间屏蔽失败" else "解除屏蔽失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportLiveDanmaku(request: LiveDanmakuReportRequest): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isBlank()) return@withContext Result.failure(Exception("请先登录"))
            val realRoomId = resolveRealRoomId(request.roomId)
            val resp = api.reportLiveDanmaku(
                roomId = realRoomId,
                targetUid = request.uid,
                message = request.message,
                reason = request.reason.apiReason,
                ts = request.reportTime,
                sign = request.sign,
                reasonId = request.reason.id,
                idStr = request.dmid,
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message.ifBlank { "举报失败" }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportSuperChat(request: LiveSuperChatReportRequest): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isBlank()) return@withContext Result.failure(Exception("请先登录"))
            val realRoomId = resolveRealRoomId(request.roomId)
            val resp = api.reportLiveSuperChat(
                id = request.messageId,
                roomId = realRoomId,
                uid = request.uid,
                message = request.message,
                reason = request.reason.apiReason,
                ts = request.reportTime,
                reasonId = request.reason.id.toString(),
                token = request.token,
                idStr = request.messageId.toString(),
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message.ifBlank { "举报醒目留言失败" }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取直播流 URL
     */
    suspend fun getLivePlayUrl(roomId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            com.android.purebilibili.core.util.Logger.d("LiveRepo", "🔴 Fetching live URL for roomId=$roomId(real=$realRoomId)")
            val resp = api.getLivePlayUrl(roomId = realRoomId)
            com.android.purebilibili.core.util.Logger.d("LiveRepo", "🔴 Live API response: code=${resp.code}, msg=${resp.message}")
            
            // 尝试从新 xlive API 结构获取 URL
            val playurlInfo = resp.data?.playurl_info
            if (playurlInfo != null) {
                com.android.purebilibili.core.util.Logger.d("LiveRepo", "🔴 Using new xlive API structure")
                val streams = playurlInfo.playurl?.stream ?: emptyList()
                // 优先选择 http_hls，其次 http_stream
                val stream = streams.find { it.protocolName == "http_hls" }
                    ?: streams.find { it.protocolName == "http_stream" }
                    ?: streams.firstOrNull()
                
                val format = stream?.format?.firstOrNull()
                val codec = format?.codec?.firstOrNull()
                val urlInfo = codec?.url_info?.firstOrNull()
                
                if (codec != null && urlInfo != null) {
                    val url = urlInfo.host + codec.baseUrl + urlInfo.extra
                    com.android.purebilibili.core.util.Logger.d("LiveRepo", " Xlive URL: ${url.take(100)}...")
                    return@withContext Result.success(url)
                }
            }
            
            // 回退到旧 API 结构
            com.android.purebilibili.core.util.Logger.d("LiveRepo", "🔴 Trying legacy durl structure...")
            val url = resp.data?.durl?.firstOrNull()?.url
            if (url != null) {
                com.android.purebilibili.core.util.Logger.d("LiveRepo", " Legacy URL: ${url.take(100)}...")
                return@withContext Result.success(url)
            }
            
            android.util.Log.e("LiveRepo", " No URL found in response")
            Result.failure(Exception("无法获取直播流"))
        } catch (e: Exception) {
            android.util.Log.e("LiveRepo", " getLivePlayUrl failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取直播流（带画质信息）- 用于画质切换
     */
    suspend fun getLivePlayUrlWithQuality(
        roomId: Long,
        qn: Int = 10000,
        onlyAudio: Boolean = false
    ): Result<LivePlayUrlData> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            com.android.purebilibili.core.util.Logger.d("LiveRepo", "🔴 Fetching live URL with quality for roomId=$roomId(real=$realRoomId), qn=$qn, onlyAudio=$onlyAudio")

            com.android.purebilibili.core.util.Logger.d("LiveRepo", "🔴 Using xlive API as primary stream source...")
            val resp = api.getLivePlayUrl(
                roomId = realRoomId,
                quality = qn,
                onlyAudio = if (onlyAudio) 1 else null,
                signedParams = signWithWbi(emptyMap())
            )

            if (resp.code == 0 && resp.data != null) {
                val xliveQualities = resp.data.playurl_info?.playurl?.gQnDesc.orEmpty()
                if (xliveQualities.isNotEmpty() || !resp.data.quality_description.isNullOrEmpty()) {
                    // 「web+app 混合取流」：web 流成功后再尝试合并 app 端流，失败则回退 web 原数据。
                    val mixed = tryMergeAppStream(realRoomId, qn, onlyAudio, resp.data)
                    return@withContext Result.success(mixed)
                }
                val legacyResp = try {
                    api.getLivePlayUrlLegacy(cid = realRoomId, qn = qn)
                } catch (e: Exception) {
                    android.util.Log.w("LiveRepo", "Legacy API failed: ${e.message}")
                    null
                }
                val mergedData = if (legacyResp?.code == 0 && legacyResp.data != null) {
                    resp.data.copy(
                        quality_description = legacyResp.data.quality_description,
                        current_quality = legacyResp.data.current_quality.takeIf { it > 0 } ?: resp.data.current_quality
                    )
                } else {
                    resp.data
                }
                com.android.purebilibili.core.util.Logger.d("LiveRepo", " Merged data: qualityList=${mergedData.quality_description?.map { it.desc }}")
                Result.success(mergedData)
            } else {
                val legacyResp = try {
                    api.getLivePlayUrlLegacy(cid = realRoomId, qn = qn)
                } catch (e: Exception) {
                    android.util.Log.w("LiveRepo", "Legacy API failed: ${e.message}")
                    null
                }
                if (legacyResp?.code == 0 && legacyResp.data != null) {
                    com.android.purebilibili.core.util.Logger.w("LiveRepo", "🔴 xlive API unavailable, falling back to legacy durl response")
                    Result.success(legacyResp.data)
                } else {
                    Result.failure(Exception("获取直播流失败: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveRepo", " getLivePlayUrlWithQuality failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 「web+app 混合取流」：以 web 端数据为主，尝试获取 app 端（platform=android）流并与其合并。
     *
     * app 端取流失败或返回异常时静默回退到 [webData]，保持现有 web 播放行为不变。
     */
    private suspend fun tryMergeAppStream(
        realRoomId: Long,
        qn: Int,
        onlyAudio: Boolean,
        webData: LivePlayUrlData
    ): LivePlayUrlData {
        val appResp = try {
            api.getLivePlayUrlApp(
                roomId = realRoomId,
                quality = qn,
                onlyAudio = if (onlyAudio) 1 else null,
                signedParams = signWithWbi(emptyMap())
            )
        } catch (e: Exception) {
            android.util.Log.w("LiveRepo", " App stream API failed, fallback to web: ${e.message}")
            null
        }
        val appData = appResp?.takeIf { it.code == 0 }?.data
        if (appData == null) {
            return webData
        }
        val merged = LiveMixedPlaybackPolicy.merge(web = webData, app = appData) ?: webData
        com.android.purebilibili.core.util.Logger.d(
            "LiveRepo",
            " Mixed web+app: webStreams=${webData.playurl_info?.playurl?.stream?.size}, " +
                "mergedStreams=${merged.playurl_info?.playurl?.stream?.size}"
        )
        return merged
    }
    /**
     * 发送直播弹幕
     */
    suspend fun sendDanmaku(roomId: Long, msg: String): Result<Boolean> {
        return sendDanmaku(LiveDanmakuSendRequest(roomId = roomId, message = msg))
    }

    suspend fun sendDanmaku(request: LiveDanmakuSendRequest): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(request.roomId)
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))

            val signedParams = signWithWbi(mapOf("web_location" to "444.8"))
            val resp = try {
                api.sendLiveDanmaku(
                    signedParams = signedParams,
                    roomId = realRoomId,
                    msg = request.message,
                    color = request.color,
                    fontsize = request.fontSize,
                    mode = request.mode,
                    bubble = request.bubble,
                    roomType = request.roomType,
                    jumpFrom = request.jumpFrom,
                    replyMid = request.replyMid,
                    replyAttr = request.replyAttr,
                    replyUname = request.replyUname,
                    replayDmid = request.replayDmid,
                    statistics = request.statistics,
                    dmType = request.dmType,
                    emoticonOptions = request.emoticonOptions,
                    csrf = csrf,
                    csrfToken = csrf
                )
            } catch (e: Exception) {
                if (signedParams.isEmpty()) throw e
                api.sendLiveDanmaku(
                    roomId = realRoomId,
                    msg = request.message,
                    color = request.color,
                    fontsize = request.fontSize,
                    mode = request.mode,
                    csrf = csrf,
                    csrfToken = csrf
                )
            }

            if (resp.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.message.ifBlank { "发送失败" }))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 直播间点赞 (上报)
     */
    suspend fun clickLike(
        roomId: Long,
        uid: Long,
        anchorId: Long,
        clickTime: Int = 1
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            
            val resp = api.clickLikeLiveRoom(
                clickTime = clickTime.coerceAtLeast(1),
                roomId = realRoomId,
                uid = uid,
                anchorId = anchorId,
                csrf = csrf,
                csrfToken = csrf
            )
            
            if (resp.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.message))
            }
        } catch (e: Exception) {

            // 点赞失败静默处理
            Result.failure(e)
        }
    }

    /**
     * 获取直播弹幕表情
     * 返回: Map<关键词, 图片URL>
     */
    suspend fun getEmoticons(roomId: Long): Result<Map<String, String>> {
        return getLiveEmoticonPackages(roomId).map { packages ->
            packages
                .flatMap { it.items }
                .filter { it.emoji.isNotBlank() && it.url.isNotBlank() }
                .associate { it.emoji to it.url }
        }
    }

    suspend fun getLiveEmoticonPackages(roomId: Long): Result<List<LiveEmoticonPackage>> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val resp = api.getLiveEmoticons(roomId = realRoomId)
            if (resp.code == 0 && resp.data?.data != null) {
                val packages = resp.data.data.map { pkg ->
                    LiveEmoticonPackage(
                        id = pkg.pkg_id,
                        name = pkg.pkg_name.ifBlank { "表情" },
                        items = pkg.emoticons
                            ?.mapNotNull { emotion ->
                                if (emotion.emoji.isBlank() || emotion.url.isBlank()) return@mapNotNull null
                                LiveEmoticonItem(
                                    emoji = emotion.emoji,
                                    url = emotion.url,
                                    description = emotion.des,
                                    emoticonUnique = emotion.emoticon_unique,
                                    emoticonOptions = buildLiveEmoticonOptions(
                                        emoji = emotion.emoji,
                                        url = emotion.url,
                                        emoticonUnique = emotion.emoticon_unique
                                    )
                                )
                            }
                            .orEmpty()
                    )
                }.filter { it.items.isNotEmpty() }
                com.android.purebilibili.core.util.Logger.d("LiveRepo", " Fetched ${packages.sumOf { it.items.size }} emoticons for room $roomId(real=$realRoomId)")
                Result.success(packages)
            } else {
                Result.failure(Exception(resp.msg))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun buildLiveEmoticonOptions(
        emoji: String,
        url: String,
        emoticonUnique: String = ""
    ): String {
        return JSONObject()
            .put("emoticon_unique", emoticonUnique.ifBlank { emoji })
            .put("bulge_display", 0)
            .put(
                "emoticon_player",
                JSONObject()
                    .put("emoji", emoji)
                    .put("url", url)
            )
            .toString()
    }

    private fun parseLiveColorInt(raw: String): Int {
        val normalized = raw.removePrefix("#")
        return normalized.toLongOrNull(16)?.toInt() ?: 0
    }
}
