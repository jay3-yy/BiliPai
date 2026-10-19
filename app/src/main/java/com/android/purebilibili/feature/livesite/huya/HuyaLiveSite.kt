package com.android.purebilibili.feature.livesite.huya

import com.android.purebilibili.core.network.buildAppDns
import com.android.purebilibili.feature.livesite.LiveSite
import com.android.purebilibili.feature.livesite.LiveSiteArea
import com.android.purebilibili.feature.livesite.LiveSiteCategory
import com.android.purebilibili.feature.livesite.LiveSiteQuality
import com.android.purebilibili.feature.livesite.LiveSiteRoom
import com.android.purebilibili.feature.livesite.LiveSiteRoomDetail
import com.android.purebilibili.feature.livesite.LiveSiteRoomPage
import com.android.purebilibili.feature.livesite.LiveSiteStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HuyaLiveSite(
    private val client: OkHttpClient = defaultClient(),
    private val cookie: String = "",
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val random: () -> Double = { Math.random() }
) : LiveSite {

    override val id: String = HUYA_ID
    override val displayName: String = "虎牙直播"

    override suspend fun getCategories(): Result<List<LiveSiteCategory>> = runCatching {
        TOP_CATEGORIES.map { parent ->
            val body = httpGet(
                "https://live.cdn.huya.com/liveconfig/game/bussLive?bussType=${parent.id}",
                apiHeaders()
            )
            LiveSiteCategory(id = parent.id, name = parent.name, areas = parseSubCategories(body))
        }
    }

    override suspend fun getRecommendRooms(page: Int): Result<LiveSiteRoomPage> = runCatching {
        val body = httpGet(
            "https://www.huya.com/cache.php?m=LiveList&do=getLiveListByPage&tagAll=0&page=$page",
            apiHeaders(
                mapOf(
                    "Origin" to "https://www.huya.com",
                    "Referer" to "https://www.huya.com/"
                )
            )
        )
        parseRoomList(body)
    }

    override suspend fun getCategoryRooms(areaId: String, page: Int): Result<LiveSiteRoomPage> = runCatching {
        val body = httpGet(
            "https://www.huya.com/cache.php?m=LiveList&do=getLiveListByPage&tagAll=0&gameId=$areaId&page=$page",
            apiHeaders()
        )
        parseRoomList(body)
    }

    override suspend fun searchRooms(keyword: String, page: Int): Result<LiveSiteRoomPage> = runCatching {
        val start = (page - 1) * 20
        val body = httpGet(
            "https://search.cdn.huya.com/?m=Search&do=getSearchContent&q=$keyword&uid=0&v=4&typ=-5&livestate=0&rows=20&start=$start",
            apiHeaders()
        )
        parseSearchRooms(body)
    }

    override suspend fun getRoomDetail(roomId: String): Result<LiveSiteRoomDetail> = runCatching {
        val body = httpGet(
            "https://mp.huya.com/cache.php?m=Live&do=profileRoom&roomid=$roomId&showSecret=1",
            apiHeaders(
                mapOf(
                    "Accept" to "*/*",
                    "Origin" to "https://www.huya.com",
                    "Referer" to "https://www.huya.com/",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-site"
                )
            )
        )
        parseRoomDetail(roomId, body)
    }

    override suspend fun getPlayUrls(
        detail: LiveSiteRoomDetail,
        qualityId: String?
    ): Result<List<LiveSiteStream>> = runCatching {
        val playData = json.decodeFromString<HuyaPlayData>(detail.playData)
        val bitRate = qualityId?.toIntOrNull() ?: 0
        val headers = mapOf(
            "Referer" to "https://www.huya.com/",
            "Origin" to "https://www.huya.com",
            "User-Agent" to PLAYER_UA
        )
        playData.lines.map { line ->
            val anti = HuyaAnticode.build(
                streamName = line.streamName,
                presenterUid = line.presenterUid,
                antiCode = line.flvAntiCode,
                nowMillis = nowMillis(),
                random = random()
            )
            var url = "${line.flvUrl}/${line.streamName}.flv?$anti&codec=264"
            if (bitRate > 0) url += "&ratio=$bitRate"
            LiveSiteStream(
                url = url,
                title = line.cdnType.ifBlank { "线路" },
                contentType = "video/x-flv",
                headers = headers
            )
        }
    }

    private fun apiHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val base = LinkedHashMap<String, String>()
        base["User-Agent"] = WEB_UA
        if (cookie.isNotBlank()) base["Cookie"] = cookie
        base.putAll(extra)
        return base
    }

    private suspend fun httpGet(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("虎牙请求失败: HTTP ${response.code}")
            }
            response.body.string()
        }
    }

    internal fun parseSubCategories(body: String): List<LiveSiteArea> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { item ->
            val obj = item.jsonObject
            val gid = obj["gid"]?.str() ?: return@mapNotNull null
            LiveSiteArea(
                id = gid,
                name = obj["gameFullName"]?.str().orEmpty(),
                pic = "https://huyaimg.msstatic.com/cdnimage/game/$gid-MS.jpg"
            )
        }
    }

    internal fun parseRoomList(body: String): LiveSiteRoomPage {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root.obj("data")
        val datas = data["datas"]?.jsonArray ?: JsonArray(emptyList())
        val items = datas.map { it.jsonObject }.map { item ->
            LiveSiteRoom(
                roomId = item["profileRoom"].str(),
                title = item["introduction"].str().ifBlank { item["roomName"].str() },
                nick = item["nick"].str(),
                cover = normalizeCover(item["screenshot"].str()),
                avatar = item["avatar180"].str(),
                area = item["gameFullName"].str(),
                watching = item["totalCount"].str(),
                live = true
            )
        }
        val page = data["page"].intOrZero() ?: 0
        val totalPage = data["totalPage"].intOrZero() ?: 0
        return LiveSiteRoomPage(items = items, hasMore = page < totalPage)
    }

    internal fun parseSearchRooms(body: String): LiveSiteRoomPage {
        val root = json.parseToJsonElement(body).jsonObject
        val response = root.obj("response")
        val queryList = response.obj("3")["docs"]?.jsonArray ?: JsonArray(emptyList())
        val responseList = response.obj("1")["docs"]?.jsonArray ?: JsonArray(emptyList())
        val items = queryList.map { it.jsonObject }.map { doc ->
            val uid = doc["uid"].str()
            val yyid = doc["yyid"].str()
            val roomId = findRoomId(responseList, uid, yyid).orEmpty().ifBlank { doc["room_id"].str() }
            LiveSiteRoom(
                roomId = roomId,
                title = doc["game_introduction"].str().ifBlank { doc["game_roomName"].str() },
                nick = doc["game_nick"].str(),
                cover = normalizeCover(doc["game_screenshot"].str()),
                avatar = doc["game_imgUrl"].str(),
                area = doc["gameName"].str(),
                watching = doc["game_total_count"].str(),
                live = true
            )
        }
        return LiveSiteRoomPage(items = items, hasMore = queryList.isNotEmpty())
    }

    internal fun parseRoomDetail(roomId: String, body: String): LiveSiteRoomDetail {
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["status"]?.intOrZero() ?: 0
        val data = root.obj("data")
        val stream = data.obj("stream")
        if (status != 200 || stream.isEmpty()) {
            return offlineDetail(roomId)
        }
        val baseList = stream["baseSteamInfoList"]?.jsonArray ?: JsonArray(emptyList())
        val flvLines = stream.obj("flv")["multiLine"]?.jsonArray ?: JsonArray(emptyList())
        val lines = flvLines.mapNotNull { flvItem ->
            val flv = flvItem.jsonObject
            if (flv["url"].str().isBlank()) return@mapNotNull null
            val matchKey = flv["cdnType"].str().ifBlank { flv["sCdnType"].str() }
            val base = baseList.firstOrNull { it.jsonObject["sCdnType"].str() == matchKey }?.jsonObject
                ?: return@mapNotNull null
            HuyaLine(
                flvUrl = base["sFlvUrl"].str(),
                streamName = base["sStreamName"].str(),
                flvAntiCode = base["sFlvAntiCode"].str(),
                presenterUid = base["lChannelId"].asLong(),
                cdnType = flv["sCdnType"].str()
            )
        }.filter { it.flvUrl.isNotBlank() && it.streamName.isNotBlank() }

        val liveData = data.obj("liveData")
        val bitRateInfoStr = liveData["bitRateInfo"].str()
        val rateArray = if (!bitRateInfoStr.isNullOrBlank()) {
            runCatching { json.decodeFromString<JsonArray>(bitRateInfoStr) }.getOrNull()
        } else {
            stream.obj("flv")["rateArray"]?.jsonArray
        }
        val bitRates = mutableListOf<HuyaBitRate>()
        rateArray?.forEach { item ->
            val obj = item.jsonObject
            val name = obj["sDisplayName"].str()
            if (name.isNotBlank() && bitRates.none { it.name == name }) {
                bitRates.add(HuyaBitRate(bitRate = obj["iBitRate"].intOrZero() ?: 0, name = name))
            }
        }
        if (bitRates.isEmpty()) {
            bitRates.add(HuyaBitRate(0, "原画"))
            bitRates.add(HuyaBitRate(2000, "高清"))
        }

        val liveStatus = data["liveStatus"].str()
        val live = liveStatus == "ON" || liveStatus == "REPLAY"
        val profile = data.obj("profileInfo")
        val playData = json.encodeToString(HuyaPlayData(lines = lines, bitRates = bitRates))
        return LiveSiteRoomDetail(
            roomId = roomId,
            title = liveData["introduction"].str(),
            nick = profile["nick"].str(),
            cover = liveData["screenshot"].str(),
            avatar = profile["avatar180"].str(),
            area = liveData["gameFullName"].str(),
            watching = liveData["userCount"].str(),
            live = live,
            notice = data["welcomeText"].str(),
            introduction = liveData["introduction"].str(),
            qualities = bitRates.map { LiveSiteQuality(id = it.bitRate.toString(), name = it.name) },
            playData = playData
        )
    }

    private fun offlineDetail(roomId: String): LiveSiteRoomDetail = LiveSiteRoomDetail(
        roomId = roomId,
        title = "",
        nick = "",
        cover = "",
        live = false,
        playData = json.encodeToString(HuyaPlayData(lines = emptyList(), bitRates = emptyList()))
    )

    private fun findRoomId(responseList: JsonArray, targetUid: String, targetYyid: String): String? {
        if (targetUid.isBlank() && targetYyid.isBlank()) return null
        return responseList.firstOrNull { item ->
            val obj = item.jsonObject
            obj["uid"]?.str() == targetUid && obj["yyid"]?.str() == targetYyid
        }?.jsonObject?.get("room_id")?.str()
    }

    private fun normalizeCover(cover: String): String =
        if (cover.isNotBlank() && !cover.contains("?")) "$cover?x-oss-process=style/w338_h190&" else cover

    @Serializable
    private data class HuyaPlayData(
        val lines: List<HuyaLine>,
        val bitRates: List<HuyaBitRate>
    )

    @Serializable
    private data class HuyaLine(
        val flvUrl: String,
        val streamName: String,
        val flvAntiCode: String,
        val presenterUid: Long,
        val cdnType: String
    )

    @Serializable
    private data class HuyaBitRate(
        val bitRate: Int,
        val name: String
    )

    companion object {
        const val HUYA_ID = "huya"
        private const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36"
        internal const val PLAYER_UA =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36"
        private val TOP_CATEGORIES = listOf(
            LiveSiteCategory(id = "1", name = "网游"),
            LiveSiteCategory(id = "2", name = "单机"),
            LiveSiteCategory(id = "8", name = "娱乐"),
            LiveSiteCategory(id = "3", name = "手游")
        )
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .dns(buildAppDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonElement?.str(): String = this?.jsonPrimitive?.contentOrNull ?: ""
private fun JsonElement?.intOrZero(): Int? = this?.jsonPrimitive?.intOrNull
private fun JsonElement?.asLong(): Long {
    return when (this) {
        null -> 0L
        is JsonPrimitive -> contentOrNull?.toLongOrNull() ?: longOrNull ?: 0L
        else -> 0L
    }
}
private fun JsonObject?.obj(key: String): JsonObject = this?.get(key)?.jsonObject ?: JsonObject(emptyMap())
