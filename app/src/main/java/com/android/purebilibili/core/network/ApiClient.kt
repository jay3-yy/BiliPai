// 文件路径: core/network/ApiClient.kt
package com.android.purebilibili.core.network

import android.content.Context
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

interface BilibiliApi {
    // 获取基本信息
    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): NavResponse

    // 获取用户统计信息
    @GET("x/web-interface/nav/stat")
    suspend fun getNavStat(): NavStatResponse

    // 获取历史记录
    @GET("x/web-interface/history/cursor")
    suspend fun getHistoryList(@Query("ps") ps: Int = 20): ListResponse<HistoryData>

    // 获取用户创建的所有收藏夹
    @GET("x/v3/fav/folder/created/list-all")
    suspend fun getFavFolders(@Query("up_mid") mid: Long): FavFolderResponse

    // 获取特定收藏夹的内容
    @GET("x/v3/fav/resource/list")
    suspend fun getFavoriteListStub(@Query("media_id") mediaId: Long, @Query("ps") ps: Int = 20): ListResponse<FavoriteData>

    // 推荐视频
    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun getRecommendParams(@QueryMap params: Map<String, String>): RecommendResponse

    // 视频详情
    @GET("x/web-interface/view")
    suspend fun getVideoInfo(@Query("bvid") bvid: String): VideoDetailResponse

    // 播放地址接口
    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrl(@QueryMap params: Map<String, String>): PlayUrlResponse

    // 相关视频
    @GET("x/web-interface/archive/related")
    suspend fun getRelatedVideos(@Query("bvid") bvid: String): RelatedResponse

    // 弹幕
    @GET("x/v1/dm/list.so")
    suspend fun getDanmakuXml(@Query("oid") cid: Long): ResponseBody

    // 🔥🔥 [新增] 获取评论列表
    @GET("x/v2/reply/main")
    suspend fun getReplyList(
        @Query("type") type: Int = 1, // 1表示视频
        @Query("oid") oid: Long,      // aid (av号)
        @Query("mode") mode: Int = 3, // 3:热度, 2:时间
        @Query("next") next: Int = 0, // 页码
        @Query("ps") ps: Int = 20     // 每页数量
    ): ReplyResponse
    @GET("x/emote/user/panel/web")
    suspend fun getEmotes(
        @Query("business") business: String = "reply"
    ): EmoteResponse
}


interface SearchApi {
    @GET("x/web-interface/search/square")
    suspend fun getHotSearch(@Query("limit") limit: Int = 10): HotSearchResponse

    @GET("x/web-interface/search/all/v2")
    suspend fun search(@QueryMap params: Map<String, String>): SearchResponse
}

interface PassportApi {
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): QrCodeResponse

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(@Query("qrcode_key") key: String): Response<PollResponse>
}

object NetworkModule {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.bilibili.com")

                val cookieBuilder = StringBuilder()
                val buvid3 = TokenManager.buvid3Cache
                if (!buvid3.isNullOrEmpty()) cookieBuilder.append("buvid3=$buvid3;")
                val sessData = TokenManager.sessDataCache
                if (!sessData.isNullOrEmpty()) cookieBuilder.append("SESSDATA=$sessData;")

                if (cookieBuilder.isNotEmpty()) builder.header("Cookie", cookieBuilder.toString())

                chain.proceed(builder.build())
            }
            .build()
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
}