// 文件路径: core/network/ApiClient.kt
package com.android.purebilibili.core.network

import android.content.Context
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.network.policy.HomeFeedAnonymizerRuntime
import com.android.purebilibili.core.network.policy.resolveHardcodedDnsFallback
import com.android.purebilibili.core.network.policy.resolveHomeFeedCookieAnonymizerDecision
import com.android.purebilibili.core.network.policy.shouldEnableTrustAllCertificates
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.StoredAccountSession
import com.android.purebilibili.data.model.response.*
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal const val BANGUMI_PLAY_URL_PATH = "pgc/player/web/v2/playurl"
internal const val BANGUMI_PLAY_URL_LEGACY_PATH = "pgc/player/web/playurl"

private class AppSessionCookieJar : okhttp3.CookieJar {
    private val cookieLock = Any()
    private val cookieStore = mutableMapOf<String, MutableList<okhttp3.Cookie>>()

    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        val host = url.host
        synchronized(cookieLock) {
            val existingCookies = cookieStore.getOrPut(host) { mutableListOf() }
            cookies.forEach { newCookie ->
                existingCookies.removeAll { it.name == newCookie.name }
                existingCookies.add(newCookie)
                com.android.purebilibili.core.util.Logger.d("CookieJar", " Saved cookie: ${newCookie.name} for $host")
            }
        }
    }

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        if (resolveHomeFeedCookieAnonymizerDecision(
                pluginEnabled = HomeFeedAnonymizerRuntime.enabled,
                host = url.host,
                encodedPath = url.encodedPath
            )
        ) {
            com.android.purebilibili.core.util.Logger.d(
                "CookieJar",
                " 初见推荐匿名化首页推荐请求: ${url.encodedPath}, clearCookieHeader=true"
            )
            return emptyList()
        }

        val cookies = mutableListOf<okhttp3.Cookie>()

        synchronized(cookieLock) {
            cookieStore[url.host]?.let { cookies.addAll(it) }
        }

        var buvid3 = TokenManager.buvid3Cache
        if (buvid3.isNullOrEmpty()) {
            buvid3 = UUID.randomUUID().toString() + "infoc"
            TokenManager.buvid3Cache = buvid3
        }
        if (cookies.none { it.name == "buvid3" }) {
            cookies.add(
                okhttp3.Cookie.Builder()
                    .domain(url.host)
                    .name("buvid3")
                    .value(buvid3)
                    .build()
            )
        }

        val biliBiliDomain = if (url.host.endsWith("bilibili.com")) "bilibili.com" else url.host
        val sessData = TokenManager.sessDataCache
        if (!sessData.isNullOrEmpty()) {
            cookies.removeAll { it.name == "SESSDATA" }
            cookies.add(
                okhttp3.Cookie.Builder()
                    .domain(biliBiliDomain)
                    .name("SESSDATA")
                    .value(sessData)
                    .build()
            )
        }

        val biliJct = TokenManager.csrfCache
        if (!biliJct.isNullOrEmpty()) {
            cookies.removeAll { it.name == "bili_jct" }
            cookies.add(
                okhttp3.Cookie.Builder()
                    .domain(biliBiliDomain)
                    .name("bili_jct")
                    .value(biliJct)
                    .build()
            )
        }

        TokenManager.midCache?.takeIf { it > 0L }?.let { mid ->
            if (cookies.none { it.name == "DedeUserID" }) {
                cookies.add(
                    okhttp3.Cookie.Builder()
                        .domain(biliBiliDomain)
                        .name("DedeUserID")
                        .value(mid.toString())
                        .build()
                )
            }
        }

        if (url.encodedPath.contains("playurl") || url.encodedPath.contains("pgc/view")) {
            com.android.purebilibili.core.util.Logger.d(
                "CookieJar",
                " ${url.encodedPath} request: domain=$biliBiliDomain, hasSess=${!sessData.isNullOrEmpty()}, hasCsrf=${!biliJct.isNullOrEmpty()}"
            )
        }

        return cookies
    }

    fun clear() {
        synchronized(cookieLock) {
            cookieStore.clear()
        }
    }
}

/** A cookie jar isolated from the main account, used only for playback authorization. */
private class PlaybackAccountCookieJar(account: StoredAccountSession) : okhttp3.CookieJar {
    private val cookieLock = Any()
    private val cookies = mutableMapOf(
        "SESSDATA" to account.sessData,
        "bili_jct" to account.csrf,
        "DedeUserID" to account.mid.toString(),
        "buvid3" to account.buvid3
    ).filterValues { it.isNotBlank() }.toMutableMap()

    override fun saveFromResponse(url: okhttp3.HttpUrl, responseCookies: List<okhttp3.Cookie>) {
        synchronized(cookieLock) {
            responseCookies.forEach { cookie -> cookies[cookie.name] = cookie.value }
        }
    }

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        val domain = if (url.host.endsWith("bilibili.com")) "bilibili.com" else url.host
        return synchronized(cookieLock) {
            cookies.map { (name, value) ->
                okhttp3.Cookie.Builder().domain(domain).name(name).value(value).build()
            }
        }
    }
}

/**
 * Bilibili 主 API 接口
 * 
 * 功能模块分区:
 * - 用户信息 (L30-45): getNavInfo, getNavStat, getHistoryList, getFavFolders, getFavoriteList
 * - 推荐/热门 (L50-70): getRecommendParams, getPopularVideos, getRegionVideos
 * - 直播 (L75-140): getLiveList, getFollowedLive, getLivePlayUrl 等
 * - 视频播放 (L145-185): getVideoInfo, getPlayUrl, getDanmakuXml 等
 * - 评论 (L195-225): getReplyList, getEmotes, getReplyReply
 * - 用户交互 (L230-295): 点赞/投币/收藏/关注 等
 * - 稍后再看 (L300-320): getWatchLaterList, addToWatchLater, deleteFromWatchLater
 */
interface BilibiliApi {
    // ==================== 用户信息模块 ====================
    @GET("x/web-interface/zone")
    suspend fun getIpZone(): IpLocationResponse

    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): NavResponse

    @GET("x/web-interface/nav/stat")
    suspend fun getNavStat(): NavStatResponse

    @GET("x/member/web/account")
    suspend fun getMemberAccount(): MemberAccountResponse

    @retrofit2.http.FormUrlEncoded
    @POST("x/member/web/sign/update")
    suspend fun updateMemberSign(
        @retrofit2.http.Field("user_sign") userSign: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    //  [New] 获取用户卡片信息 (轻量级用户信息)
    @GET("x/web-interface/card")
    suspend fun getUserCard(
        @Query("mid") mid: Long,
        @Query("photo") photo: Boolean = true
    ): UserCardResponse

    @GET("x/web-interface/card")
    suspend fun getUserCardRaw(
        @Query("mid") mid: Long,
        @Query("photo") photo: Boolean = true
    ): okhttp3.ResponseBody

    @GET("x/note/list/archive")
    suspend fun getPrivateVideoNoteIds(
        @Query("oid") oid: Long,
        @Query("oid_type") oidType: Int = 0,
        @Query("csrf") csrf: String? = null
    ): VideoNoteArchiveListResponse

    @GET("x/note/info")
    suspend fun getPrivateVideoNoteInfo(
        @Query("oid") oid: Long,
        @Query("oid_type") oidType: Int = 0,
        @Query("note_id") noteId: String
    ): VideoNoteInfoResponse

    @GET("x/note/is_forbid")
    suspend fun getVideoNoteForbidState(
        @Query("aid") aid: Long
    ): VideoNoteForbidResponse

    @retrofit2.http.FormUrlEncoded
    @POST("x/note/add")
    suspend fun saveVideoNote(
        @retrofit2.http.FieldMap fields: Map<String, String>
    ): VideoNoteSaveResponse

    @retrofit2.http.FormUrlEncoded
    @POST("x/note/del")
    suspend fun deleteVideoNote(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("note_id") noteId: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @GET("x/note/publish/list/archive")
    suspend fun getPublicVideoNoteList(
        @Query("oid") oid: Long,
        @Query("oid_type") oidType: Int = 0,
        @Query("ps") pageSize: Int = 10,
        @Query("pn") pageNumber: Int = 1
    ): PublicVideoNoteListResponse

    @GET("x/note/publish/info")
    suspend fun getPublicVideoNoteInfo(
        @Query("cvid") cvid: Long
    ): PublicVideoNoteInfoResponse

    @GET("x/web-interface/history/cursor")
    suspend fun getHistoryList(
        @Query("ps") ps: Int = 30,
        @Query("max") max: Long? = null,            //  游标: 上一页最后一条的 oid
        @Query("view_at") viewAt: Long? = null,     //  游标: 上一页最后一条的 view_at
        @Query("business") business: String? = null //  null=省略该参数
    ): HistoryResponse

    // [新增] 删除单条历史记录
    @retrofit2.http.FormUrlEncoded
    @POST("x/v2/history/delete")
    suspend fun deleteHistoryItem(
        @retrofit2.http.Field("kid") kid: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @POST("x/v2/history/clear")
    suspend fun clearHistory(
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @POST("x/v2/history/shadow/set")
    suspend fun setHistoryShadow(
        @retrofit2.http.Field("switch") shadowSwitch: Boolean,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @GET("x/v2/history/shadow")
    suspend fun getHistoryShadow(): HistoryShadowResponse

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun getFavFolders(
        @Query("up_mid") mid: Long,
        @Query("type") type: Int? = null,
        @Query("rid") rid: Long? = null,
        @Query("web_location") webLocation: String = "333.1387"
    ): FavFolderResponse

    @GET("x/v3/fav/folder/collected/list")
    suspend fun getCollectedFavFolders(
        @Query("up_mid") mid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web"
    ): FavFolderResponse

    // [新增] 创建收藏夹
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/folder/add")
    suspend fun createFavFolder(
        @retrofit2.http.Field("title") title: String,
        @retrofit2.http.Field("intro") intro: String = "",
        @retrofit2.http.Field("privacy") privacy: Int = 0, // 0:公开, 1:私密
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @GET("x/v3/fav/resource/list")
    suspend fun getFavoriteList(
        @Query("media_id") mediaId: Long, 
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("order") order: String? = null,
        @Query("platform") platform: String = "web"
    ): FavoriteResourceResponse

    @GET("x/space/like/video")
    suspend fun getLikedVideos(
        @Query("vmid") mid: Long
    ): LikedVideosResponse

    @GET("x/space/fav/season/list")
    suspend fun getFavoriteSeasonList(
        @Query("season_id") seasonId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20
    ): FavoriteResourceResponse

    // [新增] 批量删除收藏资源 (取消收藏)
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/resource/batch-del")
    suspend fun batchDelFavResource(
        @retrofit2.http.Field("media_id") mediaId: Long,
        @retrofit2.http.Field("resources") resources: String, // 格式: oid:type (e.g. "123456:2")
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/resource/clean")
    suspend fun cleanInvalidFavResource(
        @retrofit2.http.Field("media_id") mediaId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    // ==================== 推荐/热门模块 ====================
    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun getRecommendParams(@QueryMap params: Map<String, String>): RecommendResponse
    
    //  移动端推荐流 API (需要 access_token + appkey 签名)
    @GET("https://app.bilibili.com/x/v2/feed/index")
    suspend fun getMobileFeed(@QueryMap params: Map<String, String>): MobileFeedResponse

    @GET("https://app.bilibili.com/x/feed/dislike")
    suspend fun submitMobileFeedDislike(
        @QueryMap params: Map<String, String>
    ): SimpleApiResponse
    
    @GET("x/web-interface/popular")
    suspend fun getPopularVideos(
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20
    ): PopularResponse  //  使用专用响应类型

    @GET("x/web-interface/ranking/v2")
    suspend fun getRankingVideos(
        @Query("rid") rid: Int = 0,
        @Query("type") type: String = "all"
    ): RankingResponse

    @GET("x/web-interface/popular/precious")
    suspend fun getPopularPreciousVideos(): PopularPreciousResponse

    @GET("x/web-interface/popular/series/list")
    suspend fun getWeeklySeriesList(): PopularSeriesListResponse

    @GET("x/web-interface/popular/series/one")
    suspend fun getWeeklySeriesVideos(
        @Query("number") number: Int
    ): PopularSeriesOneResponse
    
    //  [修复] 分区视频 - 使用 dynamic/region API 返回完整 stat（包含播放量）
    // 原 newlist API 不返回 stat 数据
    @GET("x/web-interface/dynamic/region")
    suspend fun getRegionVideos(
        @Query("rid") rid: Int,    // 分区 ID (如 4=游戏, 36=知识, 188=科技)
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30
    ): DynamicRegionResponse
    
    // ==================== 直播模块 ====================
    // 直播列表 - 使用 v3 API (经测试确认可用)
    @GET("https://api.live.bilibili.com/room/v3/area/getRoomList")
    suspend fun getLiveList(
        @Query("parent_area_id") parentAreaId: Int = 0,  // 0=全站
        @Query("area_id") areaId: Int = 0,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_type") sortType: String = "online"  // 按人气排序
    ): LiveResponse

    @GET("https://api.live.bilibili.com/xlive/web-interface/v1/webMain/getMoreRecList")
    suspend fun getLiveRecommendList(
        @Query("platform") platform: String = "web",
        @Query("web_location") webLocation: String = "333.1007"
    ): LiveRecommendResponse
    
    //  [新增] 获取关注的直播 - 需要登录
    @GET("https://api.live.bilibili.com/xlive/web-ucenter/user/following")
    suspend fun getFollowedLive(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("ignoreRecord") ignoreRecord: Int = 1,
        @Query("hit_ab") hitAb: Boolean = true
    ): FollowedLiveResponse
    
    //  [新增] 获取直播分区列表
    @GET("https://api.live.bilibili.com/room/v1/Area/getList")
    suspend fun getLiveAreaList(): LiveAreaListResponse
    
    //  [新增] 分区推荐直播列表 (xlive API)
    @GET("https://api.live.bilibili.com/xlive/web-interface/v1/second/getList")
    suspend fun getLiveSecondAreaList(
        @Query("platform") platform: String = "web",
        @Query("parent_area_id") parentAreaId: Int,
        @Query("area_id") areaId: Int = 0,
        @Query("page") page: Int = 1,
        @Query("sort_type") sortType: String = "online"
    ): LiveSecondAreaResponse
    
    //  [新增] 获取直播间初始化信息 (真实房间号)
    @GET("https://api.live.bilibili.com/room/v1/Room/room_init")
    suspend fun getLiveRoomInit(
        @Query("id") roomId: Long
    ): LiveRoomInitResponse
    
    //  [新增] 获取直播间详细信息 (含主播信息)
    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom")
    suspend fun getLiveRoomDetail(
        @Query("room_id") roomId: Long
    ): LiveRoomDetailResponse

    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getH5InfoByRoom")
    suspend fun getLiveRoomH5Info(
        @Query("room_id") roomId: Long
    ): ResponseBody

    @GET("https://api.live.bilibili.com/xlive/web-room/v1/dM/gethistory")
    suspend fun getLiveDanmakuHistory(
        @Query("roomid") roomId: Long
    ): ResponseBody

    @GET("https://api.live.bilibili.com/xlive/web-room/v1/dM/GetDMConfigByGroup")
    suspend fun getLiveDanmakuConfig(
        @Query("room_id") roomId: Long,
        @Query("web_location") webLocation: String = "444.8"
    ): ResponseBody

    @GET("https://live-trace.bilibili.com/xlive/rdata-interface/v1/heartbeat/webHeartBeat")
    suspend fun reportLiveHeartbeat(
        @QueryMap params: Map<String, String>
    ): ResponseBody
    
    //  [新增] 获取直播弹幕 WebSocket 信息
    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo")
    suspend fun getDanmuInfo(
        @Query("id") roomId: Long,
        @Query("type") type: Int = 0
    ): LiveDanmuInfoResponse
    
    //  [新增] 获取直播弹幕 WebSocket 信息 (Wbi 签名版 - 解决 -352 风控)
    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo")
    suspend fun getDanmuInfoWbi(
        @QueryMap params: Map<String, String>
    ): LiveDanmuInfoResponse
    
    //  [新增] 获取直播间详情（包含在线人数）
    @GET("https://api.live.bilibili.com/room/v1/Room/get_info")
    suspend fun getRoomInfo(
        @Query("room_id") roomId: Long
    ): RoomInfoResponse
    
    //  [新增] 获取直播流 URL - 使用更可靠的 xlive API
    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getLivePlayUrl(
        @Query("room_id") roomId: Long,
        @Query("protocol") protocol: String = "0,1",  // 0=http_stream, 1=http_hls
        @Query("format") format: String = "0,1,2",    // 0=flv, 1=ts, 2=fmp4
        @Query("codec") codec: String = "0,1,2",      // 0=avc, 1=hevc, 2=av1
        @Query("qn") quality: Int = 150,              // 150=高清
        @Query("platform") platform: String = "web",
        @Query("ptype") ptype: Int = 8,
        @Query("dolby") dolby: Int = 5,
        @Query("panorama") panorama: Int = 1,
        @Query("web_location") webLocation: String = "444.8",
        @Query("only_audio") onlyAudio: Int? = null,
        @QueryMap signedParams: Map<String, String> = emptyMap()
    ): LivePlayUrlResponse

    //  [新增] app 端直播流 API - 与 getLivePlayUrl 同 endpoint，但 platform=android，
    //  用于「web+app 双平台混合取流」：app 端可能返回 web 端缺失的更高画质/更优流。
    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getLivePlayUrlApp(
        @Query("room_id") roomId: Long,
        @Query("protocol") protocol: String = "0,1",  // 0=http_stream, 1=http_hls
        @Query("format") format: String = "0,1,2",    // 0=flv, 1=ts, 2=fmp4
        @Query("codec") codec: String = "0,1,2",      // 0=avc, 1=hevc, 2=av1
        @Query("qn") quality: Int = 10000,
        @Query("platform") platform: String = "android",
        @Query("device") device: String = "pad",
        @Query("mobi_app") mobiApp: String = "android",
        @Query("q") q: Int = 80,
        @Query("ptype") ptype: Int = 8,
        @Query("dolby") dolby: Int = 5,
        @Query("panorama") panorama: Int = 1,
        @Query("only_audio") onlyAudio: Int? = null,
        @QueryMap signedParams: Map<String, String> = emptyMap()
    ): LivePlayUrlResponse
    
    //  [新增] 旧版直播流 API - 可靠返回 quality_description 画质列表
    @GET("https://api.live.bilibili.com/room/v1/Room/playUrl")
    suspend fun getLivePlayUrlLegacy(
        @Query("cid") cid: Long,              // 房间号 (room_id)
        @Query("qn") qn: Int = 10000,         // 画质: 10000最高, 150高清, 80流畅
        @Query("platform") platform: String = "web"
    ): LivePlayUrlResponse

    //  [新增] 发送直播弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/msg/send")
    suspend fun sendLiveDanmaku(
        @retrofit2.http.QueryMap signedParams: Map<String, String> = emptyMap(),
        @retrofit2.http.Field("roomid") roomId: Long,
        @retrofit2.http.Field("msg") msg: String,
        @retrofit2.http.Field("color") color: Int = 16777215,
        @retrofit2.http.Field("fontsize") fontsize: Int = 25,
        @retrofit2.http.Field("mode") mode: Int = 1,
        @retrofit2.http.Field("bubble") bubble: Int = 0,
        @retrofit2.http.Field("room_type") roomType: Int = 0,
        @retrofit2.http.Field("jumpfrom") jumpFrom: Int = 0,
        @retrofit2.http.Field("reply_mid") replyMid: Long = 0,
        @retrofit2.http.Field("reply_attr") replyAttr: Int = 0,
        @retrofit2.http.Field("reply_uname") replyUname: String = "",
        @retrofit2.http.Field("replay_dmid") replayDmid: String = "",
        @retrofit2.http.Field("statistics") statistics: String = "{\"appId\":100,\"platform\":5}",
        @retrofit2.http.Field("reply_type") replyType: Int = 0,
        @retrofit2.http.Field("dm_type") dmType: Int? = null,
        @retrofit2.http.Field("emoticonOptions") emoticonOptions: String? = null,
        @retrofit2.http.Field("rnd") rnd: Long = System.currentTimeMillis() / 1000,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse


    //  [新增] 直播间点赞 (点亮/点赞上报)
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/xlive/web-ucenter/v1/like/like_report_v3")
    suspend fun clickLikeLiveRoom(
        @retrofit2.http.Field("click_time") clickTime: Int = 1, // 点击次数
        @retrofit2.http.Field("room_id") roomId: Long,
        @retrofit2.http.Field("uid") uid: Long,        // 当前用户 UID
        @retrofit2.http.Field("anchor_id") anchorId: Long, // 主播 UID
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse

    //  [新增] 获取直播弹幕表情
    @GET("https://api.live.bilibili.com/xlive/web-ucenter/v2/emoticon/GetEmoticons")
    suspend fun getLiveEmoticons(
        @Query("platform") platform: String = "pc",
        @Query("room_id") roomId: Long
    ): com.android.purebilibili.data.model.response.LiveEmoticonRootResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/liveact/shield_user")
    suspend fun shieldLiveUser(
        @retrofit2.http.Field("uid") uid: Long,
        @retrofit2.http.Field("roomid") roomId: Long,
        @retrofit2.http.Field("type") type: Int,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse

    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByUser")
    suspend fun getLiveInfoByUser(
        @QueryMap params: Map<String, String>
    ): ResponseBody

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/liveact/user_silent")
    suspend fun setLiveSilentRule(
        @retrofit2.http.Field("type") type: String,
        @retrofit2.http.Field("level") level: Int,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/xlive/web-ucenter/v1/banned/AddShieldKeyword")
    suspend fun addLiveShieldKeyword(
        @retrofit2.http.Field("keyword") keyword: String,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/xlive/web-ucenter/v1/banned/DelShieldKeyword")
    suspend fun deleteLiveShieldKeyword(
        @retrofit2.http.Field("keyword") keyword: String,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/xlive/web-ucenter/v1/dMReport/Report")
    suspend fun reportLiveDanmaku(
        @retrofit2.http.Field("id") id: Long = 0,
        @retrofit2.http.Field("roomid") roomId: Long,
        @retrofit2.http.Field("tuid") targetUid: Long,
        @retrofit2.http.Field("msg") message: String,
        @retrofit2.http.Field("reason") reason: String,
        @retrofit2.http.Field("ts") ts: Long,
        @retrofit2.http.Field("sign") sign: String,
        @retrofit2.http.Field("reason_id") reasonId: Int,
        @retrofit2.http.Field("token") token: String = "",
        @retrofit2.http.Field("dm_type") dmType: Int = 0,
        @retrofit2.http.Field("id_str") idStr: String,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String,
        @retrofit2.http.Field("visit_id") visitId: String = ""
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/av/v1/SuperChat/report")
    suspend fun reportLiveSuperChat(
        @retrofit2.http.Field("id") id: Long,
        @retrofit2.http.Field("roomid") roomId: Long,
        @retrofit2.http.Field("uid") uid: Long,
        @retrofit2.http.Field("msg") message: String,
        @retrofit2.http.Field("reason") reason: String,
        @retrofit2.http.Field("ts") ts: Long,
        @retrofit2.http.Field("sign") sign: String = "",
        @retrofit2.http.Field("reason_id") reasonId: String,
        @retrofit2.http.Field("token") token: String = "",
        @retrofit2.http.Field("id_str") idStr: String,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String,
        @retrofit2.http.Field("visit_id") visitId: String = ""
    ): SimpleApiResponse

    @GET("https://api.live.bilibili.com/av/v1/SuperChat/getMessageList")
    suspend fun getLiveSuperChatMessages(
        @Query("room_id") roomId: Long
    ): ResponseBody

    @GET("https://api.live.bilibili.com/xlive/lottery-interface/v1/lottery/getLotteryInfoWeb")
    suspend fun getLiveLotteryInfo(
        @Query("roomid") roomId: Long
    ): ResponseBody

    @GET("https://api.live.bilibili.com/xlive/general-interface/v1/rank/queryContributionRank")
    suspend fun getLiveContributionRank(
        @QueryMap params: Map<String, String>
    ): LiveContributionRankResponse


    // ==================== 视频播放模块 ====================
    @GET("x/web-interface/view")
    suspend fun getVideoInfo(@Query("bvid") bvid: String): VideoDetailResponse
    
    // [修复] 通过 aid 获取视频信息 - 用于移动端推荐流（可能只返回 aid）
    @GET("x/web-interface/view")
    suspend fun getVideoInfoByAid(@Query("aid") aid: Long): VideoDetailResponse
    
    @GET("x/tag/archive/tags")
    suspend fun getVideoTags(@Query("bvid") bvid: String): VideoTagResponse

    // [新增] 获取 AI 视频总结 (WBI 签名)
    @GET("x/web-interface/view/conclusion/get")
    suspend fun getAiConclusion(@QueryMap params: Map<String, String>): AiSummaryResponse


    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrl(@QueryMap params: Map<String, String>): PlayUrlResponse
    
    //  HTML5 降级方案 (无 Referer 鉴权，仅 MP4 格式)
    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrlHtml5(@QueryMap params: Map<String, String>): PlayUrlResponse
    
    //  [新增] 上报播放心跳（记录播放历史）
    @retrofit2.http.FormUrlEncoded
    @POST("x/click-interface/web/heartbeat")
    suspend fun reportHeartbeat(
        @retrofit2.http.FieldMap fields: Map<String, String>
    ): BaseResponse

    //  [新增] 无 WBI 签名的旧版 API (可能绕过 412)
    @GET("x/player/playurl")
    suspend fun getPlayUrlLegacy(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 16,  // MP4 格式
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "html5",
        @Query("high_quality") highQuality: Int = 1
    ): PlayUrlResponse
    
    //  [新增] 通过 aid 获取播放地址 - 用于 Story 模式
    @GET("x/player/playurl")
    suspend fun getPlayUrlByAid(
        @Query("avid") aid: Long,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 16,  // MP4 格式
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "html5",
        @Query("high_quality") highQuality: Int = 1
    ): PlayUrlResponse
    
    //  [新增] APP playurl API - 使用 access_token 获取高画质视频流 (4K/HDR/1080P60)
    @GET("https://api.bilibili.com/x/player/playurl")
    suspend fun getPlayUrlApp(@QueryMap params: Map<String, String>): PlayUrlResponse

    //  [新增] TV 投屏 playurl（投屏优先使用）
    @GET("x/tv/playurl")
    suspend fun getTvPlayUrl(@QueryMap params: Map<String, String>): PlayUrlResponse

    @GET("x/player/videoshot")
    suspend fun getVideoshot(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("index") index: Int = 1  // 是否返回时间索引，1=是
    ): VideoshotResponse

    @GET("https://bvc.bilivideo.com/pbp/data")
    suspend fun getPbpData(
        @Query("cid") cid: Long,
        @Query("bvid") bvid: String? = null,
        @Query("aid") aid: Long? = null
    ): ResponseBody
    
    //  [修复] 获取播放器信息（包含章节/看点数据）— 使用 WBI 签名版本
    @GET("x/player/wbi/v2")
    suspend fun getPlayerInfo(
        @QueryMap params: Map<String, String>
    ): PlayerInfoResponse

    //  [新增] 获取视频的完整 BGM 列表
    @GET("x/copyright-music-publicity/bgm/multiple/music")
    suspend fun getBgmMultipleMusic(
        @Query("aid") aid: Long,
        @Query("cid") cid: Long
    ): com.android.purebilibili.data.model.response.BgmMultipleMusicResponse

    @GET("x/copyright-music-publicity/bgm/detail")
    suspend fun getBgmDetail(
        @Query("music_id") musicId: String,
        @Query("aid") aid: Long,
        @Query("cid") cid: Long
    ): com.android.purebilibili.data.model.response.BgmDetailResponse

    @GET("x/copyright-music-publicity/bgm/recommend_list")
    suspend fun getBgmRecommendList(
        @Query("music_id") musicId: String,
        @Query("aid") aid: Long,
        @Query("cid") cid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 5
    ): com.android.purebilibili.data.model.response.BgmRecommendListResponse

    @GET("x/stein/edgeinfo_v2")
    suspend fun getInteractEdgeInfo(
        @Query("bvid") bvid: String,
        @Query("graph_version") graphVersion: Long,
        @Query("edge_id") edgeId: Long? = null
    ): InteractEdgeInfoResponse

    @GET("x/web-interface/archive/related")
    suspend fun getRelatedVideos(@Query("bvid") bvid: String): RelatedResponse

    //  [修复] 使用 comment.bilibili.com 弹幕端点，避免 412 错误
    @GET("https://comment.bilibili.com/{cid}.xml")
    suspend fun getDanmakuXml(@retrofit2.http.Path("cid") cid: Long): ResponseBody
    
    //  [新增] Protobuf 弹幕 API - 分段加载 (每段 6 分钟)
    @GET("https://api.bilibili.com/x/v2/dm/web/seg.so")
    suspend fun getDanmakuSeg(
        @Query("type") type: Int = 1,              // 视频类型: 1=视频
        @Query("oid") oid: Long,                   // cid
        @Query("segment_index") segmentIndex: Int  // 分段索引 (从 1 开始)
    ): ResponseBody
    
    // [新增] 弹幕元数据 API (x/v2/dm/web/view)
    // 返回 DmWebViewReply Protobuf 数据，包含高级弹幕、代码弹幕 URL、互动指令等
    @GET("https://api.bilibili.com/x/v2/dm/web/view")
    suspend fun getDanmakuView(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("pid") pid: Long
    ): ResponseBody

    // [新增] 同步弹幕个人配置（账号云同步）
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/web/config")
    suspend fun updateDanmakuWebConfig(
        @retrofit2.http.Field("dm_switch") dmSwitch: String,
        @retrofit2.http.Field("blockscroll") blockScroll: String,
        @retrofit2.http.Field("blocktop") blockTop: String,
        @retrofit2.http.Field("blockbottom") blockBottom: String,
        @retrofit2.http.Field("blockcolor") blockColor: String,
        @retrofit2.http.Field("blockspecial") blockSpecial: String,
        @retrofit2.http.Field("opacity") opacity: Float,
        @retrofit2.http.Field("dmarea") dmArea: Int,
        @retrofit2.http.Field("speedplus") speedPlus: Float,
        @retrofit2.http.Field("fontsize") fontSize: Float,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 发送弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/post")
    suspend fun sendDanmaku(
        @retrofit2.http.Field("oid") oid: Long,               // 视频 cid
        @retrofit2.http.Field("aid") aid: Long,               // 视频 aid (必需)
        @retrofit2.http.Field("type") type: Int = 1,          // 弹幕类型: 1=视频
        @retrofit2.http.Field("msg") msg: String,             // 弹幕内容
        @retrofit2.http.Field("progress") progress: Long,      // 弹幕出现时间 (毫秒)
        @retrofit2.http.Field("color") color: Int = 16777215,  // 颜色 (十进制RGB，默认白色)
        @retrofit2.http.Field("fontsize") fontsize: Int = 25,  // 字号: 18小/25中/36大
        @retrofit2.http.Field("mode") mode: Int = 1,           // 模式: 1滚动/4底部/5顶部
        @retrofit2.http.Field("pool") pool: Int = 0,           // 弹幕池: 0普通/1字幕/2特殊
        @retrofit2.http.Field("colorful") colorful: Int? = null, // 60001=大会员渐变彩色
        @retrofit2.http.Field("checkbox_type") checkboxType: Int? = null, // 4=UP身份标识
        @retrofit2.http.Field("plat") plat: Int = 1,           // 平台: 1=web
        @retrofit2.http.Field("csrf") csrf: String
    ): SendDanmakuResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/command/post")
    suspend fun sendCommandDanmaku(
        @retrofit2.http.Field("type") type: Int,
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("cid") cid: Long,
        @retrofit2.http.Field("progress") progress: Long,
        @retrofit2.http.Field("plat") plat: Int = 1,
        @retrofit2.http.Field("data") data: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): CommandDanmakuResponse

    @GET
    suspend fun getDanmakuSpecialDm(@retrofit2.http.Url url: String): ResponseBody

    // [新增] 打分弹幕提交 (x/v2/dm/command/grade/post)
    // 互动投票/打分弹幕的提交端点；grade_score 为偶数，最大 10
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/command/grade/post")
    suspend fun gradeDanmaku(
        @retrofit2.http.Field("aid") aid: Long,               // 稿件 aid
        @retrofit2.http.Field("cid") cid: Long,               // 分P cid
        @retrofit2.http.Field("progress") progress: Long,      // 弹幕出现时间 (毫秒)
        @retrofit2.http.Field("grade_id") gradeId: Long,       // 打分/投票 ID
        @retrofit2.http.Field("grade_score") gradeScore: Int,  // 分数 (偶数，最大 10)
        @retrofit2.http.Field("csrf") csrf: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    // [新增] 撤回弹幕 (2分钟内可撤回，每天3次)
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/dm/recall")
    suspend fun recallDanmaku(
        @retrofit2.http.Field("cid") cid: Long,               // 视频 cid
        @retrofit2.http.Field("dmid") dmid: Long,             // 弹幕 ID
        @retrofit2.http.Field("csrf") csrf: String
    ): DanmakuActionResponse

    // [新增] 点赞弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/thumbup/add")
    suspend fun likeDanmaku(
        @retrofit2.http.Field("oid") oid: Long,               // 视频 cid
        @retrofit2.http.Field("dmid") dmid: Long,             // 弹幕 ID
        @retrofit2.http.Field("op") op: Int = 1,              // 操作: 1点赞/2取消
        @retrofit2.http.Field("platform") platform: String = "web_player",
        @retrofit2.http.Field("csrf") csrf: String
    ): DanmakuActionResponse

    // [新增] 查询弹幕点赞状态与票数
    @GET("x/v2/dm/thumbup/stats")
    suspend fun getDanmakuThumbupStats(
        @Query("oid") oid: Long,                              // 视频 cid
        @Query("ids") ids: String                             // 逗号分隔 dmid 列表
    ): com.android.purebilibili.data.model.response.DanmakuThumbupStatsResponse

    // [新增] 举报弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/dm/report/add")
    suspend fun reportDanmaku(
        @retrofit2.http.Field("cid") cid: Long,               // 视频 cid
        @retrofit2.http.Field("dmid") dmid: Long,             // 弹幕 ID
        @retrofit2.http.Field("reason") reason: Int,          // 举报原因
        @retrofit2.http.Field("content") content: String = "", // 举报内容描述
        @retrofit2.http.Field("csrf") csrf: String
    ): DanmakuActionResponse

    // ==================== 评论模块 ====================
    // 评论主列表 (需 WBI 签名)
    @GET("x/v2/reply/wbi/main")
    suspend fun getReplyList(@QueryMap params: Map<String, String>): ReplyResponse

    // 评论主列表兼容链路
    @GET("x/v2/reply/main")
    suspend fun getReplyListMain(@QueryMap params: Map<String, String>): ReplyResponse
    
    //  [新增] 旧版评论 API - 用于时间排序 (sort=0)
    // 此 API 不需要 WBI 签名，分页更稳定
    @GET("x/v2/reply")
    suspend fun getReplyListLegacy(
        @Query("oid") oid: Long,
        @Query("type") type: Int = 1,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("sort") sort: Int = 0  // 0=按时间, 1=按点赞数, 2=按回复数
    ): ReplyResponse

    @GET("x/v2/reply/reply")
    suspend fun getReplyReply(
        @Query("oid") oid: Long,
        @Query("type") type: Int = 1,
        @Query("root") root: Long, // 根评论 ID (rpid)
        @Query("pn") pn: Int,     // 页码
        @Query("ps") ps: Int = 20 // 每页数量
    ): ReplyResponse

    @GET("x/v2/reply/count")
    suspend fun getReplyCount(
        @Query("oid") oid: Long,
        @Query("type") type: Int
    ): ReplyCountResponse

    @GET("x/polymer/web-dynamic/v1/mention/search")
    suspend fun searchMentionUsers(
        @Query("keyword") keyword: String? = null
    ): MentionSearchResponse

    // [新增] 发送评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/add")
    suspend fun addReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("message") message: String,
        @retrofit2.http.Field("plat") plat: Int = 1,
        @retrofit2.http.Field("root") root: Long? = null,
        @retrofit2.http.Field("parent") parent: Long? = null,
        @retrofit2.http.Field("pictures") pictures: String? = null,
        @retrofit2.http.Field("sync_to_dynamic") syncToDynamic: Int? = null,
        @retrofit2.http.Field("csrf") csrf: String
    ): AddReplyResponse

    // [新增] 评论图片上传（复用动态图片上传接口）
    @retrofit2.http.Multipart
    @retrofit2.http.POST("x/dynamic/feed/draw/upload_bfs")
    suspend fun uploadCommentImage(
        @retrofit2.http.Part fileUp: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("category") category: okhttp3.RequestBody,
        @retrofit2.http.Part("biz") biz: okhttp3.RequestBody,
        @retrofit2.http.Part("csrf") csrf: okhttp3.RequestBody
    ): UploadCommentImageResponse

    @retrofit2.http.Multipart
    @retrofit2.http.POST("x/dynamic/feed/draw/upload_bfs")
    suspend fun uploadPrivateMessageImage(
        @retrofit2.http.Part fileUp: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("biz") biz: okhttp3.RequestBody,
        @retrofit2.http.Part("csrf") csrf: okhttp3.RequestBody
    ): UploadCommentImageResponse

    /**
     * 获取表情包
     */
    @GET("x/emote/user/panel/web")
    suspend fun getEmotes(
        @QueryMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.EmoteResponse
    
    // [新增] 点赞评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/action")
    suspend fun likeReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("action") action: Int,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 点踩评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/hate")
    suspend fun hateReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("action") action: Int,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 删除评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/del")
    suspend fun deleteReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/top")
    suspend fun setReplyTop(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("action") action: Int,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 举报评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/report")
    suspend fun reportReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("reason") reason: Int,
        @retrofit2.http.Field("content") content: String = "",
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // ==================== 用户交互模块 ====================
    // 查询与 UP 主的关注关系
    @GET("x/relation")
    suspend fun getRelation(
        @Query("fid") fid: Long  // UP 主 mid
    ): RelationResponse

    @GET("x/relation/tags")
    suspend fun getRelationTags(): RelationTagsResponse

    @GET("x/relation/blacks")
    suspend fun getRelationBlacks(
        @Query("ps") pageSize: Int = 50,
        @Query("pn") page: Int = 1
    ): com.android.purebilibili.data.model.response.RelationBlacksResponse

    @GET("x/relation/tag/user")
    suspend fun getRelationTagUser(
        @Query("fid") fid: Long
    ): RelationTagUserResponse

    @GET("x/relation/tag")
    suspend fun getRelationTagMembers(
        @Query("tagid") tagId: Long,
        @Query("order_type") orderType: String = "",
        @Query("ps") pageSize: Int = 100,
        @Query("pn") page: Int = 1
    ): com.android.purebilibili.data.model.response.RelationTagMembersResponse

    @GET("x/relation/tag")
    suspend fun getRelationTagFollowingUsers(
        @Query("tagid") tagId: Long,
        @Query("order_type") orderType: String = "",
        @Query("ps") pageSize: Int = 100,
        @Query("pn") page: Int = 1
    ): com.android.purebilibili.data.model.response.RelationTagFollowingsResponse
    
    //  [新增] 查询视频是否已收藏
    @GET("x/v2/fav/video/favoured")
    suspend fun checkFavoured(
        @Query("aid") aid: Long
    ): FavouredResponse

    @GET("x/web-interface/archive/relation")
    suspend fun getVideoRelation(
        @Query("aid") aid: Long? = null,
        @Query("bvid") bvid: String? = null
    ): VideoRelationResponse
    
    //  [新增] 关注/取关 UP 主
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/relation/modify")
    suspend fun modifyRelation(
        @retrofit2.http.Field("fid") fid: Long,      // UP 主 mid
        @retrofit2.http.Field("act") act: Int,        // 1=关注, 2=取关, 5=拉黑, 6=解除拉黑
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("re_src") reSrc: Int? = null
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/relation/tags/addUsers")
    suspend fun addUsersToRelationTags(
        @retrofit2.http.Field("fids") fids: String,
        @retrofit2.http.Field("tagids") tagIds: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 收藏/取消收藏视频
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/resource/deal")
    suspend fun dealFavorite(
        @retrofit2.http.Field("rid") rid: Long,                    // 视频 aid
        @retrofit2.http.Field("type") type: Int = 2,               // 资源类型 2=视频
        @retrofit2.http.Field("add_media_ids") addIds: String = "", // 添加到的收藏夹 ID
        @retrofit2.http.Field("del_media_ids") delIds: String = "", // 从收藏夹移除
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/season/fav")
    suspend fun favoriteSeason(
        @retrofit2.http.Field("platform") platform: String = "web",
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/season/unfav")
    suspend fun unfavoriteSeason(
        @retrofit2.http.Field("platform") platform: String = "web",
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 点赞/取消点赞视频
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/web-interface/archive/like")
    suspend fun likeVideo(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("like") like: Int,   // 1=点赞, 2=取消点赞
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 查询是否已点赞
    @GET("x/web-interface/archive/has/like")
    suspend fun hasLiked(
        @Query("aid") aid: Long
    ): HasLikedResponse
    
    //  [新增] 投币
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/web-interface/coin/add")
    suspend fun coinVideo(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("multiply") multiply: Int,       // 投币数量 1 或 2
        @retrofit2.http.Field("select_like") selectLike: Int,  // 1=同时点赞, 0=不点赞
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 查询已投币数
    @GET("x/web-interface/archive/coins")
    suspend fun hasCoined(
        @Query("aid") aid: Long
    ): HasCoinedResponse
    
    //  [新增] 获取关注列表（用于首页显示"已关注"标签）
    @GET("x/relation/followings")
    suspend fun getFollowings(
        @Query("vmid") vmid: Long,        // 用户 mid
        @Query("pn") pn: Int = 1,         // 页码
        @Query("ps") ps: Int = 50,        // 每页数量（最大 50）
        @Query("order_type") orderType: String = ""  // 按关注顺序
    ): FollowingsResponse
    
    //  [官方适配] 获取视频在线观看人数
    @GET("x/player/online/total")
    suspend fun getOnlineCount(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long
    ): OnlineResponse
    
    // ==================== 稍后再看模块 ====================
    @GET("x/v2/history/toview")
    suspend fun getWatchLaterList(): WatchLaterResponse
    
    //  [新增] 添加到稍后再看
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/history/toview/add")
    suspend fun addToWatchLater(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 从稍后再看删除
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/history/toview/del")
    suspend fun deleteFromWatchLater(
        @retrofit2.http.Field("aid") aid: Long? = null,
        @retrofit2.http.Field("viewed") viewed: Boolean? = null,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/history/toview/clear")
    suspend fun clearWatchLater(
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
}

//  [新增] Buvid SPI 响应模型 (用于获取正确的设备指纹)
@kotlinx.serialization.Serializable
data class BuvidSpiData(
    val b_3: String = "",  // buvid3
    val b_4: String = ""   // buvid4
)

@kotlinx.serialization.Serializable
data class BuvidSpiResponse(
    val code: Int = 0,
    val data: BuvidSpiData? = null
)

//  [新增] Buvid API
interface BuvidApi {
    @GET("x/frontend/finger/spi")
    suspend fun getSpi(): BuvidSpiResponse
    
    //  Buvid 激活 (PiliPala 中关键的一步)
    @retrofit2.http.FormUrlEncoded
    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activateBuvid(
        @retrofit2.http.Field("payload") payload: String
    ): SimpleApiResponse
}

//  [新增] 开屏/壁纸 API
interface SplashApi {
    @GET("https://app.bilibili.com/x/v2/splash/list")
    suspend fun getSplashList(
        @QueryMap params: Map<String, String> // 包含 appkey, ts, sign 等
    ): com.android.purebilibili.data.model.response.SplashResponse
    
    // [新增] 品牌开屏壁纸列表 (无广告，高质量)
    @GET("https://app.bilibili.com/x/v2/splash/brand/list")
    suspend fun getSplashBrandList(
        @QueryMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.SplashBrandResponse
}

interface SearchApi {
    @GET("x/web-interface/wbi/search/default")
    suspend fun getDefaultSearch(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchDefaultResponse

    @GET("x/web-interface/search/default")
    suspend fun getDefaultSearchLegacy(): com.android.purebilibili.data.model.response.SearchDefaultResponse

    @GET("x/web-interface/wbi/search/square")
    suspend fun getHotSearch(@QueryMap params: Map<String, String>): HotSearchResponse

    @GET("https://s.search.bilibili.com/main/hotword")
    suspend fun getTrendingList(
        @Query("limit") limit: Int = 30
    ): com.android.purebilibili.data.model.response.SearchTrendingResponse

    @GET("https://app.bilibili.com/x/v2/search/recommend")
    suspend fun getSearchRecommend(
        @Query("build") build: Int = 8430300,
        @Query("channel") channel: String = "master",
        @Query("version") version: String = "8.43.0",
        @Query("c_locale") cLocale: String = "zh_CN",
        @Query("mobi_app") mobiApp: String = "android",
        @Query("platform") platform: String = "android",
        @Query("s_locale") sLocale: String = "zh_CN",
        @Query("from") from: Int = 2
    ): com.android.purebilibili.data.model.response.SearchRecommendResponse

    //  综合搜索 (不支持排序)
    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/all/v2")
    suspend fun searchAll(@QueryMap params: Map<String, String>): SearchResponse
    
    //  [修复] 分类搜索 - 支持排序和时长筛选
    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun search(@QueryMap params: Map<String, String>): SearchTypeResponse
    
    //  [新增] UP主搜索 - 专用解析
    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchUp(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchUpResponse
    
    //  [新增] 番剧搜索 - search_type=media_bangumi
    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchBangumi(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.BangumiSearchResponse

    //  [新增] 影视搜索 - search_type=media_ft
    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchMediaFt(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.BangumiSearchResponse
    
    //  [新增] 直播搜索 - search_type=live_room
    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchLive(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.LiveRoomSearchResponse

    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchLiveUser(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchLiveUserResponse

    //  [新增] 专栏搜索 - search_type=article
    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchArticle(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchArticleResponse

    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchTopic(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchTopicResponse

    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/"
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchPhoto(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchPhotoResponse
    
    //  搜索建议/联想
    @GET("https://s.search.bilibili.com/main/suggest")
    suspend fun getSearchSuggest(
        @Query("term") term: String,
        @Query("main_ver") mainVer: String = "v1",
        @Query("highlight") highlight: String = term
    ): SearchSuggestResponse
}

interface ArticleApi {
    @GET("x/article/view")
    suspend fun getArticleView(
        @QueryMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.ArticleDetailResponse
}

//  [新增] 故事模式 (竖屏短视频) API
interface StoryApi {
    // 获取故事流 (竖屏短视频列表)
    @GET("x/v2/feed/index/story")
    suspend fun getStoryFeed(
        @Query("fnval") fnval: Int = 4048,         // 视频格式参数
        @Query("fnver") fnver: Int = 0,
        @Query("force_host") forceHost: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("qn") qn: Int = 32,                  // 画质
        @Query("ps") ps: Int = 20,                  // 每页数量
        @Query("aid") aid: Long = 0,                // 可选，从此视频开始
        @Query("bvid") bvid: String = ""            // 可选，从此视频开始
    ): StoryResponse
}

//  动态 API
@kotlinx.serialization.Serializable
data class DynamicThumbRequest(
    val dyn_id_str: String,
    val up: Int,
    val spmid: String = "333.1369.0.0",
    val from_spmid: String = "333.999.0.0"
)

@kotlinx.serialization.Serializable
data class DynamicRepostRequest(
    val dyn_req: DynamicRepostDynReq,
    val web_repost_src: DynamicWebRepostSource
)

@kotlinx.serialization.Serializable
data class DynamicRepostDynReq(
    val content: DynamicRepostContent,
    val scene: Int,
    val attach_card: kotlinx.serialization.json.JsonObject?
)

@kotlinx.serialization.Serializable
data class DynamicRepostContent(
    val contents: List<DynamicRepostContentItem>
)

@kotlinx.serialization.Serializable
data class DynamicRepostContentItem(
    val raw_text: String,
    val type: Int,
    val biz_id: String
)

@kotlinx.serialization.Serializable
data class DynamicWebRepostSource(
    val dyn_id_str: String
)

@kotlinx.serialization.Serializable
data class DynamicDeleteRequest(
    val dyn_id_str: String,
    val dyn_type: Int? = null,
    val rid_str: String? = null
)

@kotlinx.serialization.Serializable
data class DynamicTopRequest(
    val dyn_str: String
)

// 动态可见范围设置：object_id 为 JSON 字符串 {"dyn_id":"...","dyn_type":N}，
// action 取 "private_pub"（仅自己）/ "public_pub"（公开）。
@kotlinx.serialization.Serializable
data class DynamicVisibilityRequest(
    val object_id: String,
    val action: String
)

internal fun buildDynamicRepostRequest(
    dynamicId: String,
    content: String
): DynamicRepostRequest {
    val contents = if (content.isBlank()) {
        emptyList()
    } else {
        listOf(
            DynamicRepostContentItem(
                raw_text = content,
                type = 1,
                biz_id = ""
            )
        )
    }
    return DynamicRepostRequest(
        dyn_req = DynamicRepostDynReq(
            content = DynamicRepostContent(contents = contents),
            scene = 4,
            attach_card = null
        ),
        web_repost_src = DynamicWebRepostSource(dyn_id_str = dynamicId)
    )
}

private const val DYNAMIC_FEED_FEATURES =
    "itemOpusStyle,listOnlyfans"

internal const val DYNAMIC_DETAIL_FEATURES =
    "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,endFooterHidden,decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,commentsNewVersion,forwardListHidden,htmlNewStyle"

internal const val SPACE_DYNAMIC_FEATURES =
    "itemOpusStyle,listOnlyfans,opusBigCover,commentsNewVersion,onlyfansVote,onlyfansAssetsV2,decorationCard,forwardListHidden,ugcDelete"

interface DynamicApi {
    //  添加 features 参数以获取 rich_text_nodes 表情数据
    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun getDynamicFeed(
        @Query("type") type: String = "all",
        @Query("offset") offset: String = "",
        @Query("update_baseline") updateBaseline: String = "",
        @Query("page") page: Int = 1,
        @Query("features") features: String = DYNAMIC_FEED_FEATURES,
        @Query("timezone_offset") timezoneOffset: Int = -480,
        @Query("platform") platform: String = "web",
        @Query("web_location") webLocation: String = "333.1365"
    ): DynamicFeedResponse
    
    //  [新增] 获取指定用户的动态列表
    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun getUserDynamicFeed(
        @QueryMap params: Map<String, String>
    ): DynamicFeedResponse

    //  [新增] 动态未读数（红点）轻量接口：只返回更新基线以上的新动态条数，
    //  供底部导航轮询使用，避免每次拉全量 feed。
    @GET("x/polymer/web-dynamic/v1/feed/all/update")
    suspend fun getDynamicUpdateCount(
        @Query("type") type: String = "all",
        @Query("update_baseline") updateBaseline: String,
        @Query("web_location") webLocation: String = "333.1365"
    ): DynamicUpdateCountResponse

    //  [新增] 获取单条动态详情（桌面端详情接口）
    @GET("x/polymer/web-dynamic/desktop/v1/detail")
    suspend fun getDynamicDetail(
        @Query("id") id: String,
        @Query("features") features: String = DYNAMIC_DETAIL_FEATURES,
        @Query("timezone_offset") timezoneOffset: Int = -480
    ): DynamicDetailResponse

    //  [降级] 旧版详情接口，某些动态类型在 desktop 接口会返回不支持
    @GET("x/polymer/web-dynamic/v1/detail")
    suspend fun getDynamicDetailFallback(
        @Query("id") id: String,
        @Query("features") features: String = DYNAMIC_DETAIL_FEATURES
    ): DynamicDetailResponse

    // 长图文/专栏 opus 详情接口，htmlNewStyle 用于兼容旧专栏正文结构。
    @GET("x/polymer/web-dynamic/v1/opus/detail")
    suspend fun getOpusDetail(
        @Query("id") id: String,
        @Query("features") features: String = DYNAMIC_DETAIL_FEATURES
    ): DynamicDetailResponse

    @GET("https://app.bilibili.com/x/topic/web/details/top")
    suspend fun getTopicDetail(
        @Query("topic_id") topicId: Long,
        @Query("source") source: String = "H5",
        @Query("web_location") webLocation: String = "333.1036"
    ): TopicDetailResponse

    @GET("x/polymer/web-dynamic/v1/feed/topic")
    suspend fun getTopicFeed(
        @Query("topic_id") topicId: Long,
        @Query("sort_by") sortBy: Int = 0,
        @Query("offset") offset: String = "",
        @Query("page_size") pageSize: Int = 20,
        @Query("source") source: String = "Web",
        @Query("features") features: String = DYNAMIC_DETAIL_FEATURES
    ): TopicFeedResponse
    
    //  [新增] 获取动态评论列表 (type=17 表示动态)
    @GET("x/v2/reply")
    suspend fun getDynamicReplies(
        @Query("oid") oid: Long,       // 动态 id_str (转为 Long)
        @Query("type") type: Int = 17, // 17 = 动态评论区
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("sort") sort: Int = 0   // 0=按时间, 1=按点赞
    ): ReplyResponse
    
    //  [新增] 发表动态评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/add")
    suspend fun addDynamicReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 17,
        @retrofit2.http.Field("message") message: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [修复] 点赞动态 - 使用新版 API
    @retrofit2.http.POST("x/dynamic/feed/dyn/thumb")
    suspend fun likeDynamic(
        @Query("csrf") csrf: String,
        @Query("csrf_token") csrfToken: String = csrf,
        @retrofit2.http.Body body: DynamicThumbRequest
    ): SimpleApiResponse
    
    //  转发动态。接口按 Web 端 JSON dyn_req 协议提交，表单字段会导致请求失败后弹窗卡住。
    @retrofit2.http.POST("x/dynamic/feed/create/dyn")
    suspend fun repostDynamic(
        @Query("csrf") csrf: String,
        @Query("platform") platform: String = "web",
        @Query("x-bili-device-req-json") deviceRequestJson: String = "{\"platform\":\"web\",\"device\":\"pc\"}",
        @Query("x-bili-web-req-json") webRequestJson: String = "{\"spm_id\":\"333.1330\"}",
        @retrofit2.http.Body body: DynamicRepostRequest
    ): SimpleApiResponse

    //  发布纯文本动态（multipart form，type=4 表示纯文本）
    @retrofit2.http.Multipart
    @retrofit2.http.POST("https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/create")
    suspend fun createDynamic(
        @retrofit2.http.Part("dynamic_id") dynamicId: Int = 0,
        @retrofit2.http.Part("type") type: Int = 4,
        @retrofit2.http.Part("rid") rid: Int = 0,
        @retrofit2.http.Part("content") content: String,
        @retrofit2.http.Part("csrf") csrf: String
    ): DynamicCreateResponse

    //  关注 UP 列表（含未读标记 has_update，供 UP 列表红点使用）
    @GET("https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/w_dyn_uplist")
    suspend fun getDynamicUplist(): UplistResponse

    @retrofit2.http.POST("x/dynamic/feed/operate/remove")
    suspend fun deleteDynamic(
        @Query("csrf") csrf: String,
        @Query("platform") platform: String = "web",
        @retrofit2.http.Body body: DynamicDeleteRequest
    ): SimpleApiResponse

    //  置顶 / 取消置顶动态（仅自己的动态，JSON body {"dyn_str": "..."}）
    @retrofit2.http.POST("x/dynamic/feed/space/set_top")
    suspend fun setDynamicTop(
        @Query("csrf") csrf: String,
        @retrofit2.http.Body body: DynamicTopRequest
    ): SimpleApiResponse

    @retrofit2.http.POST("x/dynamic/feed/space/rm_top")
    suspend fun removeDynamicTop(
        @Query("csrf") csrf: String,
        @retrofit2.http.Body body: DynamicTopRequest
    ): SimpleApiResponse

    //  动态可见范围（公开 / 仅自己）
    @retrofit2.http.POST("x/dynamic/feed/dyn/private_pub_setting")
    suspend fun setDynamicVisibility(
        @Query("csrf") csrf: String,
        @Query("platform") platform: String = "web",
        @retrofit2.http.Body body: DynamicVisibilityRequest
    ): SimpleApiResponse

    //  评论互动状态（评论精选 / 评论开关），oid 取 basic.comment_id_str
    @GET("x/v2/reply/subject/interaction-status")
    suspend fun getReplyInteractionStatus(
        @Query("oid") oid: Long,
        @Query("type") type: Int,
        @Query("web_location") webLocation: Double = 333.1369
    ): ReplyInteractionResponse

    //  修改评论互动：action 1=开启精选 2=停止精选 3=关闭评论 4=恢复评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/subject/modify")
    suspend fun modifyReplySubject(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int,
        @retrofit2.http.Field("action") action: Int,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
}

//  [新增] UP主空间 API
interface SpaceApi {
    @GET("https://app.bilibili.com/x/v2/space")
    suspend fun getSpaceAggregate(
        @Query("vmid") mid: Long,
        @Query("build") build: Int = 8430300,
        @Query("version") version: String = "8.43.0",
        @Query("c_locale") cLocale: String = "zh_CN",
        @Query("channel") channel: String = "master",
        @Query("mobi_app") mobiApp: String = "android",
        @Query("platform") platform: String = "android",
        @Query("s_locale") sLocale: String = "zh_CN"
    ): com.android.purebilibili.data.model.response.SpaceAggregateResponse

    // 获取用户详细信息 (需要 WBI 签名)
    @GET("x/space/wbi/acc/info")
    suspend fun getSpaceInfo(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SpaceInfoResponse

    @GET("x/space/wbi/acc/info")
    suspend fun getSpaceInfoRaw(@QueryMap params: Map<String, String>): okhttp3.ResponseBody
    
    // 获取用户投稿视频列表 (需要 WBI 签名)
    @GET("x/space/wbi/arc/search")
    suspend fun getSpaceVideos(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SpaceVideoResponse
    
    // 获取关注/粉丝数
    @GET("x/relation/stat")
    suspend fun getRelationStat(@Query("vmid") mid: Long): com.android.purebilibili.data.model.response.RelationStatResponse
    
    // 获取UP主播放量/获赞数
    @GET("x/space/upstat")
    suspend fun getUpStat(@Query("mid") mid: Long): com.android.purebilibili.data.model.response.UpStatResponse
    
    //  获取合集和系列列表
    @GET("x/polymer/web-space/seasons_series_list")
    suspend fun getSeasonsSeriesList(
        @Query("mid") mid: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): com.android.purebilibili.data.model.response.SeasonsSeriesListResponse
    
    //  获取合集内的视频列表
    @GET("x/polymer/web-space/seasons_archives_list")
    suspend fun getSeasonArchives(
        @Query("mid") mid: Long,
        @Query("season_id") seasonId: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_reverse") sortReverse: Boolean = false
    ): com.android.purebilibili.data.model.response.SeasonArchivesResponse
    
    //  获取系列内的视频列表
    @GET("x/series/archives")
    suspend fun getSeriesArchives(
        @Query("mid") mid: Long,
        @Query("series_id") seriesId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("sort") sort: String = "desc"
    ): com.android.purebilibili.data.model.response.SeriesArchivesResponse
    
    //  置顶视频
    @GET("x/space/top/arc")
    suspend fun getTopArc(
        @Query("vmid") vmid: Long
    ): com.android.purebilibili.data.model.response.SpaceTopArcResponse
    
    //  个人公告
    @GET("x/space/notice")
    suspend fun getNotice(
        @Query("mid") mid: Long
    ): com.android.purebilibili.data.model.response.SpaceNoticeResponse
    
    //  用户动态 (需要登录 Cookie)
    @GET("x/polymer/web-dynamic/v1/feed/space")
    suspend fun getSpaceDynamic(
        @Query("host_mid") hostMid: Long,
        @Query("offset") offset: String = "",
        @Query("timezone_offset") timezoneOffset: Int = -480,
        @Query("features") features: String = SPACE_DYNAMIC_FEATURES
    ): com.android.purebilibili.data.model.response.SpaceDynamicResponse
    
    //  [New] Get User Audio List
    @GET("https://api.bilibili.com/audio/music-service/web/song/upper")
    suspend fun getSpaceAudioList(
        @Query("uid") uid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("order") order: Int = 1,  // 1=latest, 2=hot, 3=duration
        @Query("jsonp") jsonp: String = "jsonp"
    ): com.android.purebilibili.data.model.response.SpaceAudioResponse

    // 空间图文列表。API 文档为 /opus/feed/space，返回 opus_id/content/cover/jump_url。
    @GET("x/polymer/web-dynamic/v1/opus/feed/space")
    suspend fun getSpaceArticleList(
        @QueryMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.SpaceArticleResponse
}

//  [新增] 番剧/影视 API
interface BangumiApi {
    // 番剧时间表
    @GET("pgc/web/timeline")
    suspend fun getTimeline(
        @Query("types") types: Int,      // 1=番剧 4=国创
        @Query("before") before: Int = 3,
        @Query("after") after: Int = 7
    ): com.android.purebilibili.data.model.response.BangumiTimelineResponse
    
    // 番剧索引/筛选 -  需要 st 参数（与 season_type 相同值）
    @GET("pgc/season/index/result")
    suspend fun getBangumiIndex(
        @Query("season_type") seasonType: Int,   // 1=番剧 2=电影 3=纪录片 4=国创 5=电视剧 7=综艺
        @Query("st") st: Int,                    //  [修复] 必需参数，与 season_type 相同
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 20,
        @Query("order") order: Int = 3,          // 3=综合排序，2=播放量
        @Query("season_version") seasonVersion: Int = -1,  // -1=全部
        @Query("spoken_language_type") spokenLanguageType: Int = -1,  // -1=全部
        @Query("area") area: Int = -1,           // -1=全部地区
        @Query("is_finish") isFinish: Int = -1,  // -1=全部
        @Query("copyright") copyright: String = "-1", // -1=全部
        @Query("season_status") seasonStatus: String = "-1",  // -1=全部，1=免费，4,6=大会员
        @Query("season_month") seasonMonth: Int = -1,    // -1=全部
        @Query("year") year: String = "-1",      // -1=全部
        @Query("release_date") releaseDate: String = "-1", // -1=全部
        @Query("style_id") styleId: Int = -1,    // -1=全部
        @Query("producer_id") producerId: Int = -1, // -1=全部
        @Query("sort") sort: Int = 0,
        @Query("type") type: Int = 1
    ): com.android.purebilibili.data.model.response.BangumiIndexResponse
    
    // 番剧详情 -  返回 ResponseBody 自行解析，防止 OOM
    @GET("pgc/view/web/season")
    suspend fun getSeasonDetail(
        @Query("season_id") seasonId: Long? = null,
        @Query("ep_id") epId: Long? = null
    ): ResponseBody
    
    // 番剧播放地址 - PiliPlus parity path
    @GET(BANGUMI_PLAY_URL_PATH)
    suspend fun getBangumiPlayUrl(
        @QueryMap params: Map<String, String>
    ): ResponseBody

    @GET(BANGUMI_PLAY_URL_LEGACY_PATH)
    suspend fun getBangumiPlayUrlLegacy(
        @QueryMap params: Map<String, String>
    ): ResponseBody
    
    // 追番/追剧
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("pgc/web/follow/add")
    suspend fun followBangumi(
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    // 取消追番/追剧
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("pgc/web/follow/del")
    suspend fun unfollowBangumi(
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    // 更新追番/追剧状态：1=想看, 2=在看, 3=看过
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("pgc/web/follow/status/update")
    suspend fun updateBangumiFollowStatus(
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("status") status: Int,
        @retrofit2.http.Field("csrf") csrf: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    //  [新增] 我的追番列表
    @GET("x/space/bangumi/follow/list")
    suspend fun getMyFollowBangumi(
        @Query("vmid") vmid: Long,          // 用户 mid (登录用户的 mid)
        @Query("type") type: Int = 1,        // 1=追番 2=追剧
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30
    ): com.android.purebilibili.data.model.response.MyFollowBangumiResponse
}

interface PassportApi {
    @GET("https://api.bilibili.com/x/web-interface/nav")
    suspend fun validateCookieSession(
        @Header("Cookie") cookieHeader: String
    ): NavResponse

    // 二维码登录
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): QrCodeResponse

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(@Query("qrcode_key") key: String): Response<PollResponse>
    
    // ==========  极验验证 + 手机号/密码登录 ==========
    
    // 获取极验验证参数 (gt, challenge, token)
    @GET("x/passport-login/captcha")
    suspend fun getCaptcha(
        @Query("source") source: String = "main_web"
    ): CaptchaResponse
    
    // 发送短信验证码
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/web/sms/send")
    suspend fun sendSmsCode(
        @retrofit2.http.Field("cid") cid: Int = 86,           // 国家代码，中国大陆 = 86
        @retrofit2.http.Field("tel") tel: String,              // 手机号
        @retrofit2.http.Field("source") source: String = "main_web",
        @retrofit2.http.Field("token") token: String,          // captcha token
        @retrofit2.http.Field("challenge") challenge: String,  // 极验 challenge
        @retrofit2.http.Field("validate") validate: String,    // 极验验证结果
        @retrofit2.http.Field("seccode") seccode: String       // 极验安全码
    ): SmsCodeResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/sms/send")
    suspend fun sendSmsCodeByApp(
        // Kotlin Map is Map<String, out String> without this; R8 can break FieldMap bodies in release.
        @retrofit2.http.FieldMap params: @JvmSuppressWildcards Map<String, String>
    ): SmsCodeResponse
    
    // 短信验证码登录
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/web/login/sms")
    suspend fun loginBySms(
        @retrofit2.http.Field("cid") cid: Int = 86,
        @retrofit2.http.Field("tel") tel: String,
        @retrofit2.http.Field("code") code: Int,                // 短信验证码
        @retrofit2.http.Field("source") source: String = "main_mini",
        @retrofit2.http.Field("captcha_key") captchaKey: String, // sendSmsCode 返回的 key
        @retrofit2.http.Field("keep") keep: Int = 0,
        @retrofit2.http.Field("go_url") goUrl: String = "https://www.bilibili.com"
    ): Response<LoginResponse>  // 使用 Response 以获取 Set-Cookie

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/login/sms")
    suspend fun loginBySmsApp(
        @retrofit2.http.FieldMap params: @JvmSuppressWildcards Map<String, String>
    ): Response<LoginResponse>
    
    // 获取 RSA 公钥 (密码登录用)
    @GET("x/passport-login/web/key")
    suspend fun getWebKey(): WebKeyResponse
    
    // 密码登录
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/web/login")
    suspend fun loginByPassword(
        @retrofit2.http.Field("username") username: String,     // 手机号
        @retrofit2.http.Field("password") password: String,     // RSA 加密后的密码
        @retrofit2.http.Field("keep") keep: Int = 0,
        @retrofit2.http.Field("token") token: String,
        @retrofit2.http.Field("challenge") challenge: String,
        @retrofit2.http.Field("validate") validate: String,
        @retrofit2.http.Field("seccode") seccode: String,
        @retrofit2.http.Field("source") source: String = "main-fe-header",
        @retrofit2.http.Field("go_url") goUrl: String = "https://www.bilibili.com"
    ): Response<LoginResponse>

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/oauth2/login")
    suspend fun loginByPasswordApp(
        @retrofit2.http.FieldMap params: @JvmSuppressWildcards Map<String, String>
    ): Response<LoginResponse>
    
    // ==========  TV 端登录 (获取 access_token 用于高画质视频) ==========
    
    // TV 端申请二维码
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
    suspend fun generateTvQrCode(
        @retrofit2.http.FieldMap params: @JvmSuppressWildcards Map<String, String>
    ): TvQrCodeResponse
    
    // TV 端轮询登录状态
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
    suspend fun pollTvQrCode(
        @retrofit2.http.FieldMap params: @JvmSuppressWildcards Map<String, String>
    ): TvPollResponse

    //  [新增] TV 端刷新 Token
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/h5/refresh")
    suspend fun refreshToken(
        @retrofit2.http.FieldMap params: @JvmSuppressWildcards Map<String, String>
    ): com.android.purebilibili.data.model.response.TvTokenRefreshResponse
}

// ==================== 音频 API ====================
interface AudioApi {
    // 🎵 获取音频基本信息
    @GET("audio/music-service-c/web/song/info")
    suspend fun getSongInfo(
        @Query("sid") sid: Long
    ): com.android.purebilibili.data.model.response.SongInfoResponse

    // 🎵 获取音频流地址
    @GET("audio/music-service-c/web/url")
    suspend fun getSongStream(
        @Query("sid") sid: Long,
        @Query("privilege") privilege: Int = 2,
        @Query("quality") quality: Int = 2
    ): com.android.purebilibili.data.model.response.SongStreamResponse

    // 🎵 获取歌词
    @GET("audio/music-service-c/web/song/lyric")
    suspend fun getSongLyric(
        @Query("sid") sid: Long
    ): com.android.purebilibili.data.model.response.SongLyricResponse
}

// ==================== 私信 API (api.vc.bilibili.com) ====================
interface MessageApi {
    // 获取未读私信数
    @GET("session_svr/v1/session_svr/single_unread")
    suspend fun getUnreadCount(
        @Query("unread_type") unreadType: Int = 0,
        @Query("show_unfollow_list") showUnfollowList: Int = 1,
        @Query("show_dustbin") showDustbin: Int = 1,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.MessageUnreadResponse

    @GET("https://api.bilibili.com/x/msgfeed/unread")
    suspend fun getFeedUnread(
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.1365"
    ): com.android.purebilibili.data.model.response.MessageFeedUnreadResponse

    @GET("https://api.bilibili.com/x/msgfeed/reply")
    suspend fun getReplyFeed(
        @Query("id") cursor: Long? = null,
        @Query("reply_time") cursorTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164"
    ): com.android.purebilibili.data.model.response.MessageFeedReplyResponse

    @GET("https://api.bilibili.com/x/msgfeed/at")
    suspend fun getAtFeed(
        @Query("id") cursor: Long? = null,
        @Query("at_time") cursorTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164"
    ): com.android.purebilibili.data.model.response.MessageFeedAtResponse

    @GET("https://api.bilibili.com/x/msgfeed/like")
    suspend fun getLikeFeed(
        @Query("id") cursor: Long? = null,
        @Query("like_time") cursorTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164"
    ): com.android.purebilibili.data.model.response.MessageFeedLikeResponse

    @GET("https://message.bilibili.com/x/sys-msg/query_notify_list")
    suspend fun getSystemNotices(
        @Query("cursor") cursor: Long? = null,
        @Query("page_size") pageSize: Int = 20,
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0
    ): com.android.purebilibili.data.model.response.SystemNoticeResponse

    @GET("https://message.bilibili.com/x/sys-msg/update_cursor")
    suspend fun updateSystemNoticeCursor(
        @Query("csrf") csrf: String,
        @Query("cursor") cursor: Long,
        @Query("has_up") hasUp: Int = 0,
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.bilibili.com/x/msgfeed/del")
    suspend fun deleteFeedItem(
        @retrofit2.http.Field("tp") type: Int,
        @retrofit2.http.Field("id") id: Long,
        @retrofit2.http.Field("build") build: Int = 0,
        @retrofit2.http.Field("mobi_app") mobiApp: String = "web",
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.bilibili.com/x/msgfeed/notice")
    suspend fun setFeedNotice(
        @retrofit2.http.Field("id") id: Long,
        @retrofit2.http.Field("notice_state") noticeState: Int,
        @retrofit2.http.Field("tp") type: Int = 0,
        @retrofit2.http.Field("platform") platform: String = "web",
        @retrofit2.http.Field("build") build: Int = 0,
        @retrofit2.http.Field("mobi_app") mobiApp: String = "web",
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    // 获取会话列表
    @GET("session_svr/v1/session_svr/get_sessions")
    suspend fun getSessions(
        @Query("session_type") sessionType: Int = 4,  // 4=所有
        @Query("group_fold") groupFold: Int = 1,
        @Query("unfollow_fold") unfollowFold: Int = 0,
        @Query("sort_rule") sortRule: Int = 2,
        @Query("size") size: Int = 20,
        @Query("build") build: Int = 0,  //  需要此参数才能获取account_info
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.999",  //  网页位置标识
        @Query("pn") pn: Int = 1,  //  页码 (第几页)
        @Query("end_ts") endTs: Long = 0  //  结束时间戳 (游标)
    ): com.android.purebilibili.data.model.response.SessionListResponse

    @GET("session_svr/v1/session_svr/session_detail")
    suspend fun getSessionDetail(
        @Query("talker_id") talkerId: Long,
        @Query("session_type") sessionType: Int,
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.SessionDetailResponse

    @GET("link_setting/v1/link_setting/is_limit")
    suspend fun getSessionLimit(
        @Query("uid") uid: Long,
        @Query("type") type: Int = 1
    ): com.android.purebilibili.data.model.response.MessageSessionLimitResponse

    @GET("link_setting/v1/link_setting/get_msg_dnd")
    suspend fun getMsgDnd(
        @Query("own_uid") ownUid: Long,
        @Query("uids") uid: Long? = null,
        @Query("group_ids") groupId: Long? = null,
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.MessageDndResponse

    @GET("link_setting/v1/link_setting/get_session_ss")
    suspend fun getSessionPushSetting(
        @Query("talker_uid") talkerUid: Long,
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.MessageSessionPushResponse
    
    // 获取私信消息记录
    @GET("svr_sync/v1/svr_sync/fetch_session_msgs")
    suspend fun fetchSessionMsgs(
        @Query("talker_id") talkerId: Long,
        @Query("session_type") sessionType: Int = 1,
        @Query("size") size: Int = 20,
        @Query("begin_seqno") beginSeqno: Long = 0,
        @Query("end_seqno") endSeqno: Long = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.MessageHistoryResponse
    
    // 发送私信
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("web_im/v1/web_im/send_msg")
    suspend fun sendMsg(
        @retrofit2.http.Field("msg[sender_uid]") senderUid: Long,
        @retrofit2.http.Field("msg[receiver_id]") receiverId: Long,
        @retrofit2.http.Field("msg[receiver_type]") receiverType: Int = 1,
        @retrofit2.http.Field("msg[msg_type]") msgType: Int = 1,
        @retrofit2.http.Field("msg[content]") content: String,
        @retrofit2.http.Field("msg[timestamp]") timestamp: Long,
        @retrofit2.http.Field("msg[dev_id]") devId: String,
        @retrofit2.http.Field("msg[new_face_version]") newFaceVersion: Int = 1,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SendMessageResponse
    
    // 设置会话已读
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/update_ack")
    suspend fun updateAck(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("ack_seqno") ackSeqno: Long,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    // 置顶/取消置顶会话
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/set_top")
    suspend fun setTop(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("op_type") opType: Int,  // 0=置顶, 1=取消置顶
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    // 移除会话
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/remove_session")
    suspend fun removeSession(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("link_setting/v1/link_setting/set_msg_dnd")
    suspend fun setMsgDnd(
        @retrofit2.http.Field("uid") ownUid: Long?,
        @retrofit2.http.Field("setting") setting: Int,
        @retrofit2.http.Field("dnd_uid") dndUid: Long?,
        @retrofit2.http.Field("dnd_group_id") dndGroupId: Long?,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("link_setting/v1/link_setting/set_push_ss")
    suspend fun setPushSetting(
        @retrofit2.http.Field("talker_uid") talkerUid: Long,
        @retrofit2.http.Field("setting") setting: Int,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/update_intercept")
    suspend fun updateIntercept(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("status") status: Int,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/batch_update_dustbin_ack")
    suspend fun batchUpdateDustbinAck(
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/batch_rm_dustbin")
    suspend fun batchRemoveDustbin(
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
}

object NetworkModule {
    internal var appContext: Context? = null
    private val appSessionCookieJar = AppSessionCookieJar()
    private var playbackAccountKey: String? = null
    private var playbackAccountApi: BilibiliApi? = null
    private var playbackAccountBangumiApi: BangumiApi? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun clearRuntimeCookies() {
        appSessionCookieJar.clear()
    }

    @Synchronized
    fun clearPlaybackAccountClient() {
        playbackAccountKey = null
        playbackAccountApi = null
        playbackAccountBangumiApi = null
    }

    fun playbackAccount(): StoredAccountSession? =
        appContext?.let(AccountSessionStore::getPlaybackAccount)

    /**
     * Returns a client scoped to the optional playback account. It never changes
     * the app's primary account or grants entitlements locally: Bilibili still
     * decides access from the selected account's own server-side session.
     */
    @Synchronized
    fun playbackApi(): BilibiliApi {
        val context = appContext ?: return api
        val account = AccountSessionStore.getPlaybackAccount(context) ?: return api
        if (account.mid == TokenManager.midCache) return api

        val key = "${account.mid}:${account.sessData.hashCode()}:${account.buvid3.hashCode()}"
        ensurePlaybackAccountClients(account, key)
        return requireNotNull(playbackAccountApi)
    }

    @Synchronized
    fun playbackBangumiApi(): BangumiApi {
        val context = appContext ?: return bangumiApi
        val account = AccountSessionStore.getPlaybackAccount(context) ?: return bangumiApi
        if (account.mid == TokenManager.midCache) return bangumiApi

        val key = "${account.mid}:${account.sessData.hashCode()}:${account.buvid3.hashCode()}"
        ensurePlaybackAccountClients(account, key)
        return requireNotNull(playbackAccountBangumiApi)
    }

    private fun ensurePlaybackAccountClients(account: StoredAccountSession, key: String) {
        if (playbackAccountKey == key && playbackAccountApi != null && playbackAccountBangumiApi != null) {
            return
        }
        val client = okHttpClient.newBuilder()
            .cookieJar(PlaybackAccountCookieJar(account))
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        playbackAccountApi = retrofit.create(BilibiliApi::class.java)
        playbackAccountBangumiApi = retrofit.create(BangumiApi::class.java)
        playbackAccountKey = key
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    internal fun resolveSharedNetworkProtocols(): List<Protocol> {
        return listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
    }

    internal fun resolveApiHttpCacheBudgetBytes(): Long {
        return 32L * 1024 * 1024
    }

    internal fun resolveAndroidHdLoginAppKeyHeader(encodedPath: String): String? {
        return when (encodedPath) {
            "/x/passport-login/sms/send",
            "/x/passport-login/login/sms",
            "/x/passport-login/oauth2/login" -> "android_hd"
            else -> null
        }
    }

    internal fun buildPlaybackOkHttpClient(sharedClient: OkHttpClient): OkHttpClient {
        return sharedClient.newBuilder()
            // Media playback should not inherit device-local proxy apps that may expose
            // an unavailable loopback port and break streaming with ECONNREFUSED.
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .protocols(resolveSharedNetworkProtocols())
            //  [新增] 超时配置，提高网络稳定性
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            //  [性能优化] HTTP 磁盘缓存 - 10MB，减少重复请求
            .cache(okhttp3.Cache(
                directory = java.io.File(appContext?.cacheDir ?: java.io.File("/tmp"), "okhttp_cache"),
                maxSize = resolveApiHttpCacheBudgetBytes()
            ))
            //  [性能优化] 连接池优化 - 保持更多空闲连接
            .connectionPool(okhttp3.ConnectionPool(
                maxIdleConnections = 10,
                keepAliveDuration = 5,
                timeUnit = java.util.concurrent.TimeUnit.MINUTES
            ))
            //  [新增] 自动重试和重定向
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
        
        if (shouldEnableTrustAllCertificates(BuildConfig.DEBUG)) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        }
        
        builder
            //  [Fix] 自定义 DNS 实现，绕过 OkHttp 可能被混淆内部类的问题，并添加日志
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                try {
                    val addresses = java.net.InetAddress.getAllByName(hostname).toList()
                    com.android.purebilibili.core.util.Logger.d("ApiClient", "DNS resolved: $hostname -> $addresses")
                    return addresses
                } catch (e: Exception) {
                    com.android.purebilibili.core.util.Logger.e("ApiClient", "DNS failed for $hostname: ${e.message}")
                    val fallback = resolveHardcodedDnsFallback(
                        hostname = hostname,
                        allowHardcodedIpFallback = BuildConfig.ALLOW_HARDCODED_DNS_FALLBACK
                    )
                    if (fallback != null) {
                        com.android.purebilibili.core.util.Logger.w(
                            "ApiClient",
                            "⚠️ Using Hardcoded IP for ${fallback.description}: ${fallback.ipAddress}"
                        )
                        return listOf(java.net.InetAddress.getByName(fallback.ipAddress))
                    }
                    throw e
                }
            }})
            //  [关键] 添加 CookieJar 自动管理 Cookie（参考 PiliPala）
            .cookieJar(appSessionCookieJar)
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                var referer = "https://www.bilibili.com"
                
                //  如果请求中包含 bvid，构造更具体的 Referer (解决 412 问题)
                val bvid = url.queryParameter("bvid")
                if (!bvid.isNullOrEmpty()) {
                    referer = "https://www.bilibili.com/video/$bvid"
                }
                
                //  如果是 Space API 请求，使用 space.bilibili.com 作为 Referer
                val mid = url.queryParameter("mid") ?: url.queryParameter("vmid")
                if (url.encodedPath.contains("/x/space/") && !mid.isNullOrEmpty()) {
                    referer = "https://space.bilibili.com/$mid"
                }
                
                //  [新增] 直播 API Referer 处理
                if (url.host == "api.live.bilibili.com") {
                    val roomId = url.queryParameter("room_id") ?: url.queryParameter("id")
                    referer = if (!roomId.isNullOrEmpty()) {
                        "https://live.bilibili.com/$roomId"
                    } else {
                        "https://live.bilibili.com"
                    }
                }
                
                //  [修复] 弹幕 API 需要使用视频页面作为 Referer (解决 412 问题)
                if (url.encodedPath.contains("/dm/list.so") || url.encodedPath.contains("/x/v1/dm/")) {
                    referer = "https://www.bilibili.com/video/"
                }

                //  [修复] 动态接口使用动态页 Referer/Origin，降低 412 触发概率
                val isDynamicEndpoint = url.encodedPath.contains("/x/polymer/web-dynamic/") ||
                    url.encodedPath.contains("/x/dynamic/")
                if (isDynamicEndpoint) {
                    referer = "https://t.bilibili.com/"
                }

                val isFavoriteEndpoint = url.encodedPath.contains("/x/v3/fav/") ||
                    url.encodedPath.contains("/x/space/fav/")
                if (isFavoriteEndpoint) {
                    val favoriteMid = url.queryParameter("up_mid")
                        ?: TokenManager.midCache?.takeIf { it > 0L }?.toString()
                    referer = if (favoriteMid.isNullOrEmpty()) {
                        "https://space.bilibili.com/"
                    } else {
                        "https://space.bilibili.com/$favoriteMid/favlist"
                    }
                }

                var origin = "https://www.bilibili.com"
                if (url.host == "api.live.bilibili.com") {
                    origin = "https://live.bilibili.com"
                }
                if (isDynamicEndpoint) {
                    origin = "https://t.bilibili.com"
                }
                if (isFavoriteEndpoint) {
                    origin = "https://space.bilibili.com"
                }

                val androidHdLoginAppKeyHeader = resolveAndroidHdLoginAppKeyHeader(url.encodedPath)
                val isAndroidHdLoginEndpoint = androidHdLoginAppKeyHeader != null
                val builder = original.newBuilder()
                    .header(
                        "User-Agent",
                        if (isAndroidHdLoginEndpoint) {
                            "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2"
                        } else {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                        }
                    )
                if (!isAndroidHdLoginEndpoint) {
                    builder.header("Origin", origin) //  动态 Origin 头
                }
                if (androidHdLoginAppKeyHeader != null) {
                    builder
                        .header("app-key", androidHdLoginAppKeyHeader)
                        .header("buvid", TokenManager.buvid3Cache.orEmpty())
                        .header("bili-http-engine", "cronet")
                        .header("env", "prod")
                        .header("x-bili-trace-id", "11111111111111111111111111111111:1111111111111111:0:0")
                }
                
                //  [关键修复] WBI 签名接口绝对不能设置 Referer 头，否则会失败
                // 参考：https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md
                val isWbiEndpoint = url.encodedPath.contains("/wbi/")
                if (!isWbiEndpoint && !isAndroidHdLoginEndpoint) {
                    builder.header("Referer", referer)
                }

                com.android.purebilibili.core.util.Logger.d(
                    "ApiClient",
                    " Sending request to ${original.url}, Referer: ${if (isWbiEndpoint || isAndroidHdLoginEndpoint) "OMITTED" else referer}, hasSess=${!TokenManager.sessDataCache.isNullOrEmpty()}, hasCsrf=${!TokenManager.csrfCache.isNullOrEmpty()}"
                )

                val request = builder.build()
                try {
                    val response = chain.proceed(request)
                    com.android.purebilibili.core.util.Logger.d(
                        "ApiClient",
                        " Network protocol: ${response.protocol} ${request.url.host}${request.url.encodedPath}"
                    )
                    if (response.code >= 500 || response.code == 429 || response.code == 412) {
                        com.android.purebilibili.core.util.CrashReporter.reportApiError(
                            endpoint = "${request.method} ${request.url.encodedPath}",
                            httpCode = response.code,
                            errorMessage = "HTTP ${response.code}"
                        )
                    }
                    response
                } catch (e: Exception) {
                    if (com.android.purebilibili.core.util.shouldReportApiFailure(
                            callCanceled = chain.call().isCanceled(),
                            throwable = e
                        )
                    ) {
                        com.android.purebilibili.core.util.CrashReporter.reportApiError(
                            endpoint = "${request.method} ${request.url.encodedPath}",
                            httpCode = -1,
                            errorMessage = e.message ?: e.javaClass.simpleName
                        )
                    }
                    throw e
                }
            }
            .build()
    }

    val playbackOkHttpClient: OkHttpClient by lazy {
        buildPlaybackOkHttpClient(okHttpClient)
    }
    
    //  [新增] Guest OkHttpClient - 不带登录凭证，用于风控时的降级
    // 当登录用户遭遇风控 (-351) 时，可以尝试以游客身份获取视频
    val guestOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(resolveSharedNetworkProtocols())
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            //  CookieJar 使用全新的 buvid3，不复用可能被污染的 buvid3Cache
            .cookieJar(object : okhttp3.CookieJar {
                // 为 guest 模式生成独立的 buvid3，避免复用被风控的 buvid3
                private val guestBuvid3: String by lazy { 
                    UUID.randomUUID().toString().replace("-", "") + "infoc"
                }
                
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                    // 不保存任何 cookie
                }
                
                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                    val cookies = mutableListOf<okhttp3.Cookie>()
                    
                    //  使用全新生成的 guestBuvid3，不使用 TokenManager.buvid3Cache
                    cookies.add(okhttp3.Cookie.Builder()
                        .domain(url.host)
                        .name("buvid3")
                        .value(guestBuvid3)
                        .build())
                    
                    com.android.purebilibili.core.util.Logger.d(
                        "GuestCookieJar",
                        " ${url.encodedPath} request: guest mode with fresh buvid3=${guestBuvid3.take(15)}..."
                    )
                    
                    return cookies
                }
            })
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                var referer = "https://www.bilibili.com"
                
                val bvid = url.queryParameter("bvid")
                if (!bvid.isNullOrEmpty()) {
                    referer = "https://www.bilibili.com/video/$bvid"
                }

                val isWbiEndpoint = url.encodedPath.contains("/wbi/")
                val builder = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Origin", "https://www.bilibili.com")

                if (!isWbiEndpoint) {
                    builder.header("Referer", referer)
                }
                
                val request = builder.build()
                try {
                    val response = chain.proceed(request)
                    com.android.purebilibili.core.util.Logger.d(
                        "ApiClient",
                        " Guest network protocol: ${response.protocol} ${request.url.host}${request.url.encodedPath}"
                    )
                    if (response.code >= 500 || response.code == 429 || response.code == 412) {
                        com.android.purebilibili.core.util.CrashReporter.reportApiError(
                            endpoint = "guest ${request.method} ${request.url.encodedPath}",
                            httpCode = response.code,
                            errorMessage = "HTTP ${response.code}"
                        )
                    }
                    response
                } catch (e: Exception) {
                    if (com.android.purebilibili.core.util.shouldReportApiFailure(
                            callCanceled = chain.call().isCanceled(),
                            throwable = e
                        )
                    ) {
                        com.android.purebilibili.core.util.CrashReporter.reportApiError(
                            endpoint = "guest ${request.method} ${request.url.encodedPath}",
                            httpCode = -1,
                            errorMessage = e.message ?: e.javaClass.simpleName
                        )
                    }
                    throw e
                }
            }
            .build()
    }
    
    //  [新增] Guest API - 使用 guestOkHttpClient，用于风控降级
    val guestApi: BilibiliApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(guestOkHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BilibiliApi::class.java)
    }

    val api: BilibiliApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BilibiliApi::class.java)
    }
    val passportApi: PassportApi by lazy {
        Retrofit.Builder().baseUrl("https://passport.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(PassportApi::class.java)
    }
    val searchApi: SearchApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SearchApi::class.java)
    }

    val articleApi: ArticleApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(ArticleApi::class.java)
    }
    
    //  动态 API
    val dynamicApi: DynamicApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(DynamicApi::class.java)
    }
    
    //  Buvid API (用于获取设备指纹)
    val buvidApi: BuvidApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BuvidApi::class.java)
    }
    
    //  [新增] UP主空间 API
    val spaceApi: SpaceApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SpaceApi::class.java)
    }
    
    //  [新增] 番剧/影视 API
    val bangumiApi: BangumiApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BangumiApi::class.java)
    }
    
    //  [新增] 故事模式 (竖屏短视频) API - 使用 app.bilibili.com
    val storyApi: StoryApi by lazy {
        Retrofit.Builder().baseUrl("https://app.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(StoryApi::class.java)
    }
    
    //  [新增] 开屏/壁纸 API
    val splashApi: SplashApi by lazy {
        Retrofit.Builder().baseUrl("https://app.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SplashApi::class.java)
    }
    
    //  [新增] 私信 API - 使用 api.vc.bilibili.com
    val messageApi: MessageApi by lazy {
        Retrofit.Builder().baseUrl("https://api.vc.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(MessageApi::class.java)
    }
    
    //  [新增] 音频 API - 使用 www.bilibili.com
    val audioApi: AudioApi by lazy {
        Retrofit.Builder().baseUrl("https://www.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(AudioApi::class.java)
    }
}
