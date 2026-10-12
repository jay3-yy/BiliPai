// 文件路径: data/repository/LiveRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.CancellationException
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
    val replyToUid: Long = 0L,
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
    val reportTs: Long = 0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val duration: Int = 0
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

internal fun hasPlayableLiveUrl(data: LivePlayUrlData): Boolean {
    if (data.durl.orEmpty().any { it.url.isNotBlank() }) return true
    return data.playurl_info
        ?.playurl
        ?.stream
        .orEmpty()
        .any { stream ->
            stream.format.orEmpty().any { format ->
                format.codec.orEmpty().any { codec ->
                    codec.baseUrl.isNotBlank() && codec.url_info.orEmpty().any { it.host.isNotBlank() }
                }
            }
        }
}

internal fun buildLivePlayUrlQuery(roomId: Long, qn: Int, onlyAudio: Boolean): Map<String, String> =
    buildMap {
        put("room_id", roomId.toString())
        put("protocol", "0,1")
        put("format", "0,1,2")
        put("codec", "0,1,2")
        put("qn", qn.toString())
        put("platform", "web")
        put("ptype", "8")
        put("dolby", "5")
        put("panorama", "1")
        put("web_location", "444.8")
        if (onlyAudio) put("only_audio", "1")
    }

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
                    replyToUid = reply?.long("reply_mid") ?: 0L,
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
                    ?: data.optJSONArray("shield_user_list")
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
    val switchValue: String,
    val apiValue: String,
) {
    ONLINE("在线榜", "contribution_rank", "online_rank"),
    DAILY("日榜", "today_rank", "daily_rank"),
    WEEKLY("周榜", "current_week_rank", "weekly_rank"),
    MONTHLY("月榜", "current_month_rank", "monthly_rank")
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

internal fun parseLiveVotePanel(rawJson: String): LiveVoteInfo? {
    val root = JSONObject(rawJson)
    if (root.optInt("code") != 0) return null
    return root.optJSONObject("data")?.optJSONObject("vote_info")?.let(::parseLiveVoteInfo)
}

internal fun parseLiveVoteHistory(rawJson: String): List<LiveVoteInfo> {
    val root = JSONObject(rawJson)
    if (root.optInt("code") != 0) return emptyList()
    val history = root.optJSONObject("data")?.optJSONArray("history") ?: return emptyList()
    return buildList {
        for (index in 0 until history.length()) {
            history.optJSONObject(index)?.let(::parseLiveVoteInfo)?.let(::add)
        }
    }
}

private fun parseLiveVoteInfo(json: JSONObject): LiveVoteInfo? {
    val question = json.optString("question").trim()
    if (question.isBlank()) return null
    val optionsJson = json.optJSONArray("options")
    val options = buildList {
        if (optionsJson != null) for (index in 0 until optionsJson.length()) {
            val option = optionsJson.optJSONObject(index) ?: continue
            val description = option.optString("desc").trim()
            if (description.isNotBlank()) {
                add(
                    LiveVoteOption(
                        id = option.optInt("idx"),
                        description = description,
                        percent = option.optDouble("percent", 0.0).toFloat().coerceIn(0f, 1f)
                    )
                )
            }
        }
    }
    return LiveVoteInfo(
        status = json.optInt("status"),
        question = question,
        options = options,
        durationMillis = json.optLong("duration"),
        remainingMillis = json.optLong("left_duration"),
        resultText = json.optString("result_text"),
        endTimeText = json.optString("etime_str"),
        interactionId = json.optLong("interaction_id")
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            getLiveRooms(page = 1)
        }
    }

    /**
     * BiliPai 同款 App 直播首页 feed。
     * page=1 时解析关注模块 + 分区入口 + small_card；后续页仅追加 small_card。
     */
    suspend fun getLiveFeedHome(
        page: Int = 1,
        moduleSelect: Boolean = false,
    ): Result<LiveFeedHomeSnapshot> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getLiveFeedIndex(buildLiveAppFeedParams(page = page, moduleSelect = moduleSelect))
            val data = resp.data
            if (resp.code != 0 || data == null) {
                return@withContext if (page == 1) {
                    fallbackLiveFeedHome()
                } else {
                    Result.failure(Exception(resp.message.ifBlank { "直播 feed 加载失败" }))
                }
            }
            Result.success(parseLiveFeedHomeSnapshot(data))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (page == 1) fallbackLiveFeedHome() else Result.failure(e)
        }
    }

    /**
     * BiliPai 同款二级分区列表（含 new_tags 排序 chip）。
     * 失败时回退 web second/getList。
     */
    suspend fun getLiveSecondHome(
        parentAreaId: Int,
        areaId: Int = 0,
        page: Int = 1,
        sortType: String? = null,
    ): Result<LiveFeedHomeSnapshot> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getLiveAppSecondList(
                buildLiveAppSecondListParams(
                    page = page,
                    parentAreaId = parentAreaId,
                    areaId = areaId,
                    sortType = sortType,
                )
            )
            if (resp.code == 0 && resp.data != null) {
                val checkedRespData = requireNotNull(resp.data)
                val rooms = checkedRespData.list
                    ?.filter { isUsableLiveFeedRoom(it) }
                    ?.map { it.toLiveRoom() }
                    ?.filter(::shouldKeepLiveSecondListRoom)
                    ?.distinctBy { it.roomid }
                    .orEmpty()
                val hasMore = hasMoreLiveAreaRooms(
                    loadedCount = checkedRespData.list.orEmpty().size,
                    page = page,
                    pageSize = 20,
                    hasMoreFlag = checkedRespData.hasMore,
                    totalCount = checkedRespData.count
                )
                return@withContext Result.success(
                    LiveFeedHomeSnapshot(
                        rooms = rooms,
                        sortTags = checkedRespData.newTags.orEmpty().filter { it.name.isNotBlank() || it.sortType.isNotBlank() },
                        hasMore = hasMore,
                        totalCount = checkedRespData.count,
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // fall through
        }

        getAreaRoomsPage(
            parentAreaId = parentAreaId,
            areaId = areaId,
            page = page,
            sortType = sortType?.takeIf { it.isNotBlank() } ?: "online",
        ).map { pageResult ->
            LiveFeedHomeSnapshot(
                rooms = pageResult.rooms,
                hasMore = pageResult.hasMore,
                totalCount = pageResult.totalCount,
            )
        }
    }

    private fun buildLiveAppFeedParams(page: Int, moduleSelect: Boolean): Map<String, String> {
        val params = linkedMapOf(
            "appkey" to AppSignUtils.ANDROID_APP_KEY,
            "actionKey" to "appkey",
            "build" to "8430300",
            "version" to "8.43.0",
            "c_locale" to "zh_CN",
            "s_locale" to "zh_CN",
            "channel" to "master",
            "device" to "android",
            "device_name" to "android",
            "device_type" to "0",
            "fnval" to "912",
            "disable_rcmd" to "0",
            "https_url_req" to "1",
            "mobi_app" to "android",
            "network" to "wifi",
            "page" to page.toString(),
            "platform" to "android",
            "scale" to "2",
            "ts" to AppSignUtils.getTimestamp().toString(),
        )
        TokenManager.accessTokenCache?.takeIf { it.isNotBlank() }?.let {
            params["access_key"] = it
            params["relation_page"] = "1"
        }
        if (moduleSelect) params["module_select"] = "1"
        return AppSignUtils.signForAndroidApi(params)
    }

    private fun buildLiveFavoriteTagParams(tags: List<LiveFavoriteTagEntry>? = null): Map<String, String> {
        val params = buildLiveAppFeedParams(1, false).toMutableMap()
        params.remove("sign")
        params.remove("page")
        params.remove("relation_page")
        if (tags != null) params["tags"] = tags.joinToString(",") { it.areaId.toString() }
        return AppSignUtils.signForAndroidApi(params)
    }

    suspend fun getLiveFavoriteTags(): Result<List<LiveFavoriteTagEntry>> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(api.getLiveFavoriteTagsRaw(buildLiveFavoriteTagParams()).use { it.string() })
            if (root.optInt("code", -1) != 0) return@withContext Result.failure(Exception(root.optString("message", "获取常用标签失败")))
            val tags = root.optJSONObject("data")?.optJSONArray("tags")
            Result.success(buildList {
                for (index in 0 until (tags?.length() ?: 0)) {
                    val tag = tags?.optJSONObject(index) ?: continue
                    val id = tag.optInt("id")
                    if (id <= 0) continue
                    add(LiveFavoriteTagEntry(tag.optInt("parent_id"), id, tag.optString("name"), tag.optString("pic"), tag.optString("parent_name")))
                }
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setLiveFavoriteTags(tags: List<LiveFavoriteTagEntry>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(api.setLiveFavoriteTagsRaw(buildLiveFavoriteTagParams(tags)).use { it.string() })
            if (root.optInt("code", -1) == 0) Result.success(Unit)
            else Result.failure(Exception(root.optString("message", "保存常用标签失败")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildLiveAppSecondListParams(
        page: Int,
        parentAreaId: Int,
        areaId: Int,
        sortType: String?,
    ): Map<String, String> {
        val params = linkedMapOf(
            "appkey" to AppSignUtils.ANDROID_APP_KEY,
            "actionKey" to "appkey",
            "build" to "8430300",
            "version" to "8.43.0",
            "c_locale" to "zh_CN",
            "s_locale" to "zh_CN",
            "channel" to "master",
            "device" to "android",
            "device_name" to "android",
            "device_type" to "0",
            "fnval" to "912",
            "disable_rcmd" to "0",
            "https_url_req" to "1",
            "mobi_app" to "android",
            "module_select" to "0",
            "network" to "wifi",
            "page" to page.toString(),
            "page_size" to "20",
            "platform" to "android",
            "qn" to "0",
            "tag_version" to "1",
            "scale" to "2",
            "parent_area_id" to parentAreaId.toString(),
            "area_id" to areaId.toString(),
            "ts" to AppSignUtils.getTimestamp().toString(),
        )
        TokenManager.accessTokenCache?.takeIf { it.isNotBlank() }?.let {
            params["access_key"] = it
        }
        if (!sortType.isNullOrBlank()) {
            params["sort_type"] = sortType
        }
        return AppSignUtils.signForAndroidApi(params)
    }

    private suspend fun fallbackLiveFeedHome(): Result<LiveFeedHomeSnapshot> {
        val recommend = getRecommendedLiveRooms().getOrElse { emptyList() }
        val followedPage = getFollowedLivePage(page = 1).getOrNull()
        val follow = followedPage?.items.orEmpty()
        val areas = getLiveAreaIndex().getOrElse { emptyList() }
            .map {
                LiveFeedAreaEntry(
                    title = it.name,
                    areaId = 0,
                    parentAreaId = it.id,
                )
            }
        return Result.success(
            LiveFeedHomeSnapshot(
                rooms = recommend,
                followRooms = follow,
                totalCount = followedPage?.totalCount ?: follow.size,
                areaEntries = areas,
                hasMore = recommend.size >= 20,
            )
        )
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
            if (e is CancellationException) throw e
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
                            hasMoreFlag = data?.hasMore,
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
            if (e is CancellationException) throw e
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
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "获取关注直播失败" }))
            }
            val rooms = resp.data?.list.orEmpty()
            val followedRooms = rooms.filter { it.liveStatus == 1 }

            val liveRooms = followedRooms.map { it.toLiveRoom() }
            val pageInfo = resp.data?.pageinfo
            val hasMore = when {
                pageInfo != null && pageInfo.total_page > 0 -> page < pageInfo.total_page
                rooms.size >= pageSize -> true
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            params
        }
    }

    suspend fun getLiveDanmakuHistory(roomId: Long): Result<List<LivePrefetchDanmaku>> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            parseLiveDanmakuHistoryItems(api.getLiveDanmakuHistory(realRoomId).string())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun getLiveDanmakuPermission(roomId: Long): Result<LiveDanmakuPermission> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            Result.success(parseLiveDanmakuPermission(api.getLiveDanmakuConfig(realRoomId).string()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
                                reportTs = obj.optLong("ts", obj.optLong("start_time", 0L)),
                                startTime = obj.optLong("start_time", 0L),
                                endTime = obj.optLong("end_time", 0L),
                                duration = obj.optInt("time", obj.optInt("duration", 0))
                            )
                        )
                    }
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun getLiveRedPocketInfo(roomId: Long): Result<LiveRedPocketInfo?> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            Result.success(parseLiveRedPocketInfo(api.getLiveLotteryInfo(realRoomId).string()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun getLiveVoteSnapshot(roomId: Long): Result<LiveVoteSnapshot> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            Result.success(
                LiveVoteSnapshot(
                    current = parseLiveVotePanel(api.getLiveVotePanel(realRoomId).string()),
                    history = parseLiveVoteHistory(api.getLiveVoteHistory(realRoomId).string())
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                    "type" to type.apiValue,
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
                dmType = request.dmType,
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message.ifBlank { "举报失败" }))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                reasonId = request.reason.apiReason,
                token = request.token,
                idStr = request.messageId.toString(),
                csrf = csrf,
                csrfToken = csrf
            )
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message.ifBlank { "举报醒目留言失败" }))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            val resp = api.getLivePlayUrl(signWithWbi(buildLivePlayUrlQuery(realRoomId, 150, false)))
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
            if (e is CancellationException) throw e
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
            val resp = api.getLivePlayUrl(signWithWbi(buildLivePlayUrlQuery(realRoomId, qn, onlyAudio)))
            val data = resp.data
            if (resp.code == 0 && data?.liveStatus != null && data.liveStatus != 1) {
                return@withContext Result.failure(Exception("当前直播间未开播"))
            }

            if (resp.code == 0 && resp.data != null && hasPlayableLiveUrl(requireNotNull(resp.data))) {
                val checkedRespData = requireNotNull(resp.data)
                val xliveQualities = checkedRespData.playurl_info?.playurl?.gQnDesc.orEmpty()
                if (onlyAudio || xliveQualities.isNotEmpty() || !checkedRespData.quality_description.isNullOrEmpty()) {
                    return@withContext Result.success(checkedRespData)
                }
                val legacyResp = try {
                    api.getLivePlayUrlLegacy(cid = realRoomId, qn = qn)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("LiveRepo", "Legacy API failed: ${e.message}")
                    null
                }
                val mergedData = if (legacyResp?.code == 0 && legacyResp.data != null) {
                    val checkedLegacyRespData = requireNotNull(legacyResp.data)
                    checkedRespData.copy(
                        quality_description = checkedLegacyRespData.quality_description,
                        current_quality = checkedLegacyRespData.current_quality.takeIf { it > 0 } ?: checkedRespData.current_quality
                    )
                } else {
                    checkedRespData
                }
                com.android.purebilibili.core.util.Logger.d("LiveRepo", " Merged data: qualityList=${mergedData.quality_description?.map { it.desc }}")
                Result.success(mergedData)
            } else {
                if (onlyAudio) {
                    return@withContext Result.failure(Exception(resp.message.ifBlank { "无法获取直播音频地址" }))
                }
                val legacyResp = try {
                    api.getLivePlayUrlLegacy(cid = realRoomId, qn = qn)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("LiveRepo", "Legacy API failed: ${e.message}")
                    null
                }
                if (
                    legacyResp?.code == 0 &&
                    legacyResp.data != null &&
                    hasPlayableLiveUrl(requireNotNull(legacyResp.data))
                ) {
                    val checkedLegacyRespData = requireNotNull(legacyResp.data)
                    com.android.purebilibili.core.util.Logger.w("LiveRepo", "🔴 xlive API unavailable, falling back to legacy durl response")
                    Result.success(checkedLegacyRespData)
                } else {
                    val reason = resp.message.ifBlank { "接口未返回可播放地址" }
                    Result.failure(Exception("获取直播流失败: $reason"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("LiveRepo", " getLivePlayUrlWithQuality failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
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
            val isStandaloneEmoticon = request.dmType == 1
            val resp = api.sendLiveDanmaku(
                signedParams = signedParams,
                roomId = realRoomId,
                msg = request.message,
                color = request.color,
                fontsize = request.fontSize,
                mode = request.mode,
                bubble = request.bubble,
                roomType = request.roomType.takeUnless { isStandaloneEmoticon },
                jumpFrom = request.jumpFrom.takeUnless { isStandaloneEmoticon },
                replyMid = request.replyMid.takeUnless { isStandaloneEmoticon },
                replyAttr = request.replyAttr.takeUnless { isStandaloneEmoticon },
                replyUname = request.replyUname.takeUnless { isStandaloneEmoticon },
                replayDmid = request.replayDmid.takeUnless { isStandaloneEmoticon },
                statistics = request.statistics.takeUnless { isStandaloneEmoticon },
                replyType = if (isStandaloneEmoticon) null else 0,
                dmType = request.dmType,
                emoticonOptions = request.emoticonOptions,
                csrf = csrf,
                csrfToken = csrf
            )

            if (resp.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.message.ifBlank { "发送失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e

            // 点赞失败静默处理
            Result.failure(e)
        }
    }

    /**
     * 进房上报（登录态，写入直播观看历史；对齐 PiliPlus roomEntryAction）
     */
    suspend fun reportRoomEntry(roomId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
            val resp = api.reportLiveRoomEntry(roomId = roomId, csrf = csrf, csrfToken = csrf)
            if (resp.code == 0) Result.success(true) else Result.failure(Exception(resp.message))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // 进房上报失败静默处理
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
                val packages = requireNotNull(resp.data?.data)
                    .map { it.toLiveEmoticonPackage() }
                    .filter { it.items.isNotEmpty() }
                com.android.purebilibili.core.util.Logger.d("LiveRepo", " Fetched ${packages.sumOf { it.items.size }} emoticons for room $roomId(real=$realRoomId)")
                Result.success(packages)
            } else {
                Result.failure(Exception(resp.errorMessage.ifBlank { "获取直播表情失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }


    private fun parseLiveColorInt(raw: String): Int {
        val normalized = raw.trim().removePrefix("#")
        val value = normalized.toLongOrNull(16)?.toInt() ?: return 0
        return when (normalized.length) {
            6 -> value or 0xFF000000.toInt()
            8 -> value
            else -> 0
        }
    }
}
