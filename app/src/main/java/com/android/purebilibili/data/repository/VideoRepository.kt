// 文件路径: data/repository/VideoRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.cache.PlayUrlCache
import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.InputStream
import java.util.TreeMap

object VideoRepository {
    private val api = NetworkModule.api
    private val buvidApi = NetworkModule.buvidApi

    private val QUALITY_CHAIN = listOf(120, 116, 112, 80, 74, 64, 32, 16)
    
    //  [新增] 确保 buvid3 来自 Bilibili SPI API + 激活（解决 412 问题）
    private var buvidInitialized = false
    
    private suspend fun ensureBuvid3FromSpi() {
        if (buvidInitialized) return
        try {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " Fetching buvid3 from SPI API...")
            val response = buvidApi.getSpi()
            if (response.code == 0 && response.data != null) {
                val b3 = response.data.b_3
                if (b3.isNotEmpty()) {
                    TokenManager.buvid3Cache = b3
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " buvid3 from SPI: ${b3.take(20)}...")
                    
                    //  [关键] 激活 buvid (参考 PiliPala)
                    try {
                        activateBuvid()
                        com.android.purebilibili.core.util.Logger.d("VideoRepo", " buvid activated!")
                    } catch (e: Exception) {
                        android.util.Log.w("VideoRepo", "buvid activation failed: ${e.message}")
                    }
                    
                    buvidInitialized = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoRepo", " Failed to get buvid3 from SPI: ${e.message}")
        }
    }
    
    /**
     * 公开的 buvid3 初始化函数 - 供其他 Repository 调用
     */
    suspend fun ensureBuvid3() {
        ensureBuvid3FromSpi()
    }
    
    //  激活 buvid (参考 PiliPala buvidActivate)
    private suspend fun activateBuvid() {
        val random = java.util.Random()
        val randBytes = ByteArray(32) { random.nextInt(256).toByte() }
        val endBytes = byteArrayOf(0, 0, 0, 0, 73, 69, 78, 68) + ByteArray(4) { random.nextInt(256).toByte() }
        val randPngEnd = android.util.Base64.encodeToString(randBytes + endBytes, android.util.Base64.NO_WRAP)
        
        val payload = org.json.JSONObject().apply {
            put("3064", 1)
            put("39c8", "333.999.fp.risk")
            put("3c43", org.json.JSONObject().apply {
                put("adca", "Windows") // 与 User-Agent (Windows NT 10.0) 保持一致
                put("bfe9", randPngEnd.takeLast(50))
            })
        }.toString()
        
        buvidApi.activateBuvid(payload)
    }

    // 1. 首页推荐
    suspend fun getHomeVideos(idx: Int = 0): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img ?: throw Exception("无法获取 Key")
            val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")

            val params = mapOf(
                "ps" to "10", "fresh_type" to "3", "fresh_idx" to idx.toString(),
                "feed_version" to System.currentTimeMillis().toString(), "y_num" to idx.toString()
            )
            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val feedResp = api.getRecommendParams(signedParams)
            
            //  [调试] 检查 API 是否返回 dimension 字段
            feedResp.data?.item?.take(3)?.forEachIndexed { index, item ->
                com.android.purebilibili.core.util.Logger.d("VideoRepo", 
                    " 视频[$index]: ${item.title?.take(15)}... dimension=${item.dimension} isVertical=${item.dimension?.isVertical}")
            }
            
            val list = feedResp.data?.item?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            
            //  [调试] 检查转换后的 VideoItem
            val verticalCount = list.count { it.isVertical }
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " 首页视频: total=${list.size}, vertical=$verticalCount")
            
            Result.success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    //  [新增] 热门视频
    suspend fun getPopularVideos(page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getPopularVideos(pn = page, ps = 20)
            val list = resp.data?.list?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    //  [新增] 分区视频（按分类 ID 获取视频）
    suspend fun getRegionVideos(tid: Int, page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getRegionVideos(rid = tid, pn = page, ps = 30)
            val list = resp.data?.archives?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    //  [新增] 上报播放心跳（记录到历史记录）
    suspend fun reportPlayHeartbeat(bvid: String, cid: Long, playedTime: Long = 0) = withContext(Dispatchers.IO) {
        try {
            //  隐私无痕模式检查：如果启用则跳过上报
            val context = com.android.purebilibili.core.network.NetworkModule.appContext
            if (context != null && com.android.purebilibili.core.store.SettingsManager.isPrivacyModeEnabledSync(context)) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " Privacy mode enabled, skipping heartbeat report")
                return@withContext true  // 返回成功但不实际上报
            }
            
            com.android.purebilibili.core.util.Logger.d("VideoRepo", "🔴 Reporting heartbeat: bvid=$bvid, cid=$cid, playedTime=$playedTime")
            val resp = api.reportHeartbeat(bvid = bvid, cid = cid, playedTime = playedTime, realPlayedTime = playedTime)
            com.android.purebilibili.core.util.Logger.d("VideoRepo", "🔴 Heartbeat response: code=${resp.code}, msg=${resp.message}")
            resp.code == 0
        } catch (e: Exception) {
            android.util.Log.e("VideoRepo", " Heartbeat failed: ${e.message}")
            false
        }
    }
    

    suspend fun getNavInfo(): Result<NavData> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getNavInfo()
            if (resp.code == 0 && resp.data != null) {
                Result.success(resp.data)
            } else {
                if (resp.code == -101) {
                    Result.success(NavData(isLogin = false))
                } else {
                    Result.failure(Exception("错误码: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideoDetails(bvid: String, targetQuality: Int? = null): Result<Pair<ViewInfo, PlayUrlData>> = withContext(Dispatchers.IO) {
        try {
            val viewResp = api.getVideoInfo(bvid)
            val info = viewResp.data ?: throw Exception("视频详情为空: ${viewResp.code}")
            val cid = info.cid
            
            //  [调试] 记录视频信息
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " getVideoDetails: bvid=${info.bvid}, aid=${info.aid}, cid=$cid, title=${info.title.take(20)}...")
            
            if (cid == 0L) throw Exception("CID 获取失败")

            //  [优化] 使用缓存加速重复播放
            val cachedPlayData = PlayUrlCache.get(bvid, cid)
            if (cachedPlayData != null) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " Using cached PlayUrlData for bvid=$bvid")
                return@withContext Result.success(Pair(info, cachedPlayData))
            }

            //  [优化] 根据登录和大会员状态选择起始画质
            val isLogin = !TokenManager.sessDataCache.isNullOrEmpty()
            val isVip = TokenManager.isVipCache
            
            //  [实验性功能] 读取 auto1080p 设置
            val auto1080pEnabled = try {
                val context = com.android.purebilibili.core.network.NetworkModule.appContext
                context?.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
                    ?.getBoolean("exp_auto_1080p", true) ?: true // 默认开启
            } catch (e: Exception) {
                true // 出错时默认开启
            }
            
            //  [关键修复] 优先使用传入的用户画质设置，否则使用内部逻辑
            val startQuality = targetQuality ?: when {
                isVip -> 116     // 大会员：优先 1080P+ (HDR)
                isLogin && auto1080pEnabled -> 80  //  已登录 + 开启1080p：优先 1080p
                isLogin -> 64    // 已登录非大会员（关闭1080p设置）：优先 720p
                else -> 32       // 未登录：优先 480p（避免限制）
            }
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " Selected startQuality=$startQuality (userSetting=$targetQuality, isLogin=$isLogin, isVip=$isVip, auto1080p=$auto1080pEnabled)")

            val playData = fetchPlayUrlRecursive(bvid, cid, startQuality)
                ?: throw Exception("无法获取任何画质的播放地址")

            //  支持 DASH 和 durl 两种格式
            val hasDash = !playData.dash?.video.isNullOrEmpty()
            val hasDurl = !playData.durl.isNullOrEmpty()
            if (!hasDash && !hasDurl) throw Exception("播放地址解析失败 (无 dash/durl)")

            //  [优化] 缓存结果
            PlayUrlCache.put(bvid, cid, playData, playData.quality)
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " Cached PlayUrlData for bvid=$bvid, cid=$cid")

            Result.success(Pair(info, playData))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    //  [优化] WBI Key 缓存
    private var wbiKeysCache: Pair<String, String>? = null
    private var wbiKeysTimestamp: Long = 0
    private const val WBI_CACHE_DURATION = 1000 * 60 * 30 //  优化：30分钟缓存
    
    //  412 错误冷却期（避免过快重试触发风控）
    private var last412Time: Long = 0
    private const val COOLDOWN_412_MS = 5000L // 412 后等待 5 秒

    private suspend fun getWbiKeys(): Pair<String, String> {
        val currentCheck = System.currentTimeMillis()
        val cached = wbiKeysCache
        if (cached != null && (currentCheck - wbiKeysTimestamp < WBI_CACHE_DURATION)) {
            return cached
        }

        //  [优化] 增加重试逻辑，最多 3 次尝试
        val maxRetries = 3
        var lastError: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                val navResp = api.getNavInfo()
                val wbiImg = navResp.data?.wbi_img
                
                if (wbiImg != null) {
                    val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
                    val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
                    
                    wbiKeysCache = Pair(imgKey, subKey)
                    wbiKeysTimestamp = System.currentTimeMillis()
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " WBI Keys obtained successfully (attempt $attempt)")
                    return wbiKeysCache!!
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("VideoRepo", "getWbiKeys attempt $attempt failed: ${e.message}")
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(200L * attempt) // 递增延迟
                }
            }
        }
        
        throw Exception("Wbi Keys Error after $maxRetries attempts: ${lastError?.message}")
    }

    suspend fun getPlayUrlData(bvid: String, cid: Long, qn: Int): PlayUrlData? = withContext(Dispatchers.IO) {
        //  [新增] 对于高画质请求 (>=112)，优先尝试 APP API
        val isHighQuality = qn >= 112
        val accessToken = TokenManager.accessTokenCache
        
        if (isHighQuality && !accessToken.isNullOrEmpty()) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " High quality request (qn=$qn), trying APP API first...")
            val appResult = fetchPlayUrlWithAccessToken(bvid, cid, qn)
            if (appResult != null) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " APP API success for high quality")
                return@withContext appResult
            }
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " APP API failed, fallback to Web API")
        }
        
        //  [修复] 412 错误处理：清除 WBI 密钥缓存后重试
        var result = fetchPlayUrlWithWbi(bvid, cid, qn)
        if (result == null) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " First attempt failed (likely 412), invalidating WBI keys and retrying...")
            // 清除 WBI 密钥缓存
            wbiKeysCache = null
            wbiKeysTimestamp = 0
            // 短暂延迟后重试（让服务器恢复）
            kotlinx.coroutines.delay(500)
            result = fetchPlayUrlWithWbi(bvid, cid, qn)
        }
        result
    }


    //  [v2 优化] 核心播放地址获取逻辑 - 根据登录状态区分策略
    private suspend fun fetchPlayUrlRecursive(bvid: String, cid: Long, targetQn: Int): PlayUrlData? {
        //  关键：确保有正确的 buvid3 (来自 Bilibili SPI API)
        ensureBuvid3FromSpi()
        
        val isLoggedIn = !TokenManager.sessDataCache.isNullOrEmpty()
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " fetchPlayUrlRecursive: bvid=$bvid, isLoggedIn=$isLoggedIn, targetQn=$targetQn")
        
        return if (isLoggedIn) {
            // 已登录：DASH 优先（风控宽松），HTML5 降级
            fetchDashWithFallback(bvid, cid, targetQn)
        } else {
            // 未登录：HTML5 优先（避免 412），DASH 降级
            fetchHtml5WithFallback(bvid, cid, targetQn)
        }
    }
    
    //  已登录用户：APP API 优先 -> DASH -> HTML5 降级策略
    private suspend fun fetchDashWithFallback(bvid: String, cid: Long, targetQn: Int): PlayUrlData? {
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] DASH-first strategy, qn=$targetQn")
        
        //  [新增] 如果有 access_token，优先使用 APP API 获取高画质
        val accessToken = TokenManager.accessTokenCache
        if (!accessToken.isNullOrEmpty()) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] Trying APP API first with access_token...")
            val appResult = fetchPlayUrlWithAccessToken(bvid, cid, targetQn)
            if (appResult != null && (!appResult.durl.isNullOrEmpty() || !appResult.dash?.video.isNullOrEmpty())) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] APP API success: quality=${appResult.quality}")
                return appResult
            }
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] APP API failed, trying DASH...")
        }
        
        // 尝试 DASH，最多 2 次重试
        val retryDelays = listOf(0L, 500L)
        for ((attempt, delay) in retryDelays.withIndex()) {
            if (delay > 0) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " DASH retry ${attempt + 1}...")
                kotlinx.coroutines.delay(delay)
            }
            try {
                val data = fetchPlayUrlWithWbiInternal(bvid, cid, targetQn)
                if (data != null && (!data.durl.isNullOrEmpty() || !data.dash?.video.isNullOrEmpty())) {
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] DASH success: quality=${data.quality}")
                    return data
                }
                android.util.Log.w("VideoRepo", " DASH attempt ${attempt + 1}: data is null or empty")
            } catch (e: Exception) {
                android.util.Log.w("VideoRepo", "DASH attempt ${attempt + 1} failed: ${e.message}")
                if (e.message?.contains("412") == true) {
                    last412Time = System.currentTimeMillis()
                }
            }
        }
        
        // DASH 失败，降级到 HTML5
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] DASH failed, trying HTML5 fallback...")
        val html5Data = fetchPlayUrlHtml5Fallback(bvid, cid, 80)
        if (html5Data != null && (!html5Data.durl.isNullOrEmpty() || !html5Data.dash?.video.isNullOrEmpty())) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] HTML5 fallback success: quality=${html5Data.quality}")
            return html5Data
        }
        
        //  [新增] HTML5 失败，尝试 Legacy API（无 WBI 签名）
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] HTML5 failed, trying Legacy API...")
        try {
            val legacyResult = api.getPlayUrlLegacy(bvid = bvid, cid = cid, qn = 80)
            if (legacyResult.code == 0 && legacyResult.data != null) {
                val data = legacyResult.data
                if (!data.durl.isNullOrEmpty() || !data.dash?.video.isNullOrEmpty()) {
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] Legacy API success: quality=${data.quality}")
                    return data
                }
            } else {
                android.util.Log.w("VideoRepo", "Legacy API returned code=${legacyResult.code}, msg=${legacyResult.message}")
            }
        } catch (e: Exception) {
            android.util.Log.w("VideoRepo", "[LoggedIn] Legacy API failed: ${e.message}")
        }
        
        //  [终极修复] 所有方法都失败了，尝试以游客身份获取（无登录凭证）
        // 这是为了解决"登录后反而看不了视频"的问题
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn] All auth methods failed! Trying GUEST fallback (no auth)...")
        val guestResult = fetchAsGuestFallback(bvid, cid)
        if (guestResult != null) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " [LoggedIn->Guest] Guest fallback success: quality=${guestResult.quality}")
            return guestResult
        }
        
        android.util.Log.e("VideoRepo", " [LoggedIn] All attempts failed for bvid=$bvid")
        return null
    }
    
    //  [新增] 以游客身份获取视频（忽略登录凭证）
    //  [修复] 使用 guestApi 确保不携带 SESSDATA/bili_jct
    private suspend fun fetchAsGuestFallback(bvid: String, cid: Long): PlayUrlData? {
        try {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " fetchAsGuestFallback: bvid=$bvid, cid=$cid (using guestApi)")
            
            // ✅ 使用 guestApi - 不携带登录凭证
            val guestApi = NetworkModule.guestApi
            
            val legacyResult = guestApi.getPlayUrlLegacy(
                bvid = bvid, 
                cid = cid, 
                qn = 64,  // 降低画质要求，提高成功率
                fnval = 1,  // MP4 格式
                platform = "html5",  // HTML5 平台
                highQuality = 1
            )
            
            if (legacyResult.code == 0 && legacyResult.data != null) {
                val data = legacyResult.data
                if (!data.durl.isNullOrEmpty()) {
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " Guest fallback (Legacy 64p) success")
                    return data
                }
            } else {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " Guest fallback 64p failed: code=${legacyResult.code}")
            }
            
            //  如果 64p 也失败，尝试更低画质 32p
            val lowQualityResult = guestApi.getPlayUrlLegacy(
                bvid = bvid, 
                cid = cid, 
                qn = 32,
                fnval = 1,
                platform = "html5",
                highQuality = 0
            )
            
            if (lowQualityResult.code == 0 && lowQualityResult.data != null) {
                val data = lowQualityResult.data
                if (!data.durl.isNullOrEmpty()) {
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " Guest fallback (Legacy 32p) success")
                    return data
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.w("VideoRepo", "Guest fallback failed: ${e.message}")
        }
        
        return null
    }
    
    //  未登录用户：旧版 API 优先策略（无 WBI 签名，避免 412）
    private suspend fun fetchHtml5WithFallback(bvid: String, cid: Long, targetQn: Int): PlayUrlData? {
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " [Guest] Legacy API-first strategy (no WBI)")
        
        //  [关键] 首先尝试旧版 API（无 WBI 签名）
        try {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " [Guest] Trying legacy playurl API...")
            val legacyResult = api.getPlayUrlLegacy(bvid = bvid, cid = cid, qn = 80)
            if (legacyResult.code == 0 && legacyResult.data != null) {
                val data = legacyResult.data
                if (!data.durl.isNullOrEmpty() || !data.dash?.video.isNullOrEmpty()) {
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " [Guest] Legacy API success: quality=${data.quality}")
                    return data
                }
            } else {
                android.util.Log.w("VideoRepo", "Legacy API returned code=${legacyResult.code}, msg=${legacyResult.message}")
            }
        } catch (e: Exception) {
            android.util.Log.w("VideoRepo", "[Guest] Legacy API failed: ${e.message}")
        }
        
        // 降级到 HTML5 WBI
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " [Guest] Legacy failed, trying HTML5 WBI fallback...")
        val html5Result = fetchPlayUrlHtml5Fallback(bvid, cid, 80)
        if (html5Result != null) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " [Guest] HTML5 success: quality=${html5Result.quality}")
            return html5Result
        }
        
        // 最后尝试 DASH (限 1 次)
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " [Guest] HTML5 failed, trying DASH...")
        try {
            val dashData = fetchPlayUrlWithWbiInternal(bvid, cid, targetQn)
            if (dashData != null && (!dashData.durl.isNullOrEmpty() || !dashData.dash?.video.isNullOrEmpty())) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " [Guest] DASH fallback success: quality=${dashData.quality}")
                return dashData
            }
        } catch (e: Exception) {
            android.util.Log.w("VideoRepo", "[Guest] DASH fallback failed: ${e.message}")
        }
        
        android.util.Log.e("VideoRepo", " [Guest] All attempts failed for bvid=$bvid")
        return null
    }

    //  内部方法：单次请求播放地址 (使用 fnval=4048 获取全部 DASH 流)
    private suspend fun fetchPlayUrlWithWbiInternal(bvid: String, cid: Long, qn: Int): PlayUrlData? {
        com.android.purebilibili.core.util.Logger.d("VideoRepo", "fetchPlayUrlWithWbiInternal: bvid=$bvid, cid=$cid, qn=$qn")
        
        //  使用缓存的 Keys
        val (imgKey, subKey) = getWbiKeys()
        
        //  [新增] 生成 session 参数 (buvid3 + 时间戳 MD5)
        val buvid3 = com.android.purebilibili.core.store.TokenManager.buvid3Cache ?: ""
        val timestamp = System.currentTimeMillis()
        val sessionRaw = buvid3 + timestamp.toString()
        val session = java.security.MessageDigest.getInstance("MD5")
            .digest(sessionRaw.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        val params = mapOf(
            "bvid" to bvid, "cid" to cid.toString(), "qn" to qn.toString(),
            "fnval" to "4048",  //  全部 DASH 格式，一次性获取所有可用流
            "fnver" to "0", "fourk" to "1", 
            "platform" to "pc",  //  改用 pc (Web默认值)，支持所有格式
            "high_quality" to "1",
            "try_look" to "1",  //  允许未登录用户尝试获取更高画质 (64/80)
            //  [新增] session 参数 - VIP 画质可能需要
            "session" to session,
            //  [参考 PiliPala] 以下参数经过用户验证，提高成功率
            "voice_balance" to "1",
            "gaia_source" to "pre-load",
            "web_location" to "1550101"
        )
        val signedParams = WbiUtils.sign(params, imgKey, subKey)
        val response = api.getPlayUrl(signedParams)
        
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " PlayUrl response: code=${response.code}, requestedQn=$qn, returnedQuality=${response.data?.quality}")
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " accept_quality=${response.data?.accept_quality}, accept_description=${response.data?.accept_description}")
        //  [调试] 输出 DASH 视频流 ID 列表
        val dashIds = response.data?.dash?.video?.map { it.id }?.distinct()?.sortedDescending()
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " DASH video IDs: $dashIds")
        
        if (response.code == 0) return response.data
        
        //  [优化] API 返回错误码分类处理，提供更明确的错误信息
        val errorMessage = classifyPlayUrlError(response.code, response.message)
        android.util.Log.e("VideoRepo", " PlayUrl API error: code=${response.code}, message=${response.message}, classified=$errorMessage")
        // 对于不可重试的错误，抛出明确异常
        if (response.code in listOf(-404, -403, -10403, -62002)) {
            throw Exception(errorMessage)
        }
        return null
    }
    
    //  [新增] 使用 access_token 获取高画质视频流 (4K/HDR/1080P60)
    private suspend fun fetchPlayUrlWithAccessToken(bvid: String, cid: Long, qn: Int): PlayUrlData? {
        val accessToken = com.android.purebilibili.core.store.TokenManager.accessTokenCache
        if (accessToken.isNullOrEmpty()) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " No access_token available, fallback to Web API")
            return null
        }
        
        com.android.purebilibili.core.util.Logger.d("VideoRepo", " fetchPlayUrlWithAccessToken: bvid=$bvid, qn=$qn, accessToken=${accessToken.take(10)}...")
        
        //  [修复] 必须使用 TV appkey，因为 access_token 是通过 TV 登录获取的
        // 根据 B站 API 文档：通过某一组 APPKEY/APPSEC 获取到的 access_token，之后的 API 调用也必须使用同一组
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to "4048",  // 全部 DASH 格式
            "fnver" to "0",
            "fourk" to "1",
            "access_key" to accessToken,
            "appkey" to AppSignUtils.TV_APP_KEY,  //  使用 TV appkey (与登录时一致)
            "ts" to AppSignUtils.getTimestamp().toString(),
            "platform" to "android",
            "mobi_app" to "android_tv_yst",  //  TV 端标识
            "device" to "android"
        )
        
        val signedParams = AppSignUtils.signForTvLogin(params)  //  使用 TV 签名
        
        try {
            val response = api.getPlayUrlApp(signedParams)
            
            val dashIds = response.data?.dash?.video?.map { it.id }?.distinct()?.sortedDescending()
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " APP PlayUrl response: code=${response.code}, qn=$qn, dashIds=$dashIds")
            
            if (response.code == 0 && response.data != null) {
                // 检查是否真的获取到了高画质流
                val hasHighQuality = dashIds?.any { it >= qn } == true
                if (hasHighQuality) {
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " APP API returned high quality: $dashIds")
                    return response.data
                } else {
                    com.android.purebilibili.core.util.Logger.d("VideoRepo", " APP API didn't return target quality $qn, available: $dashIds")
                }
            } else {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " APP API error: code=${response.code}, msg=${response.message}")
            }
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " APP API exception: ${e.message}")
        }
        
        return null
    }

    //  [重构] 带 HTML5 降级的播放地址获取
    private suspend fun fetchPlayUrlWithWbi(bvid: String, cid: Long, qn: Int): PlayUrlData? {
        try {
            return fetchPlayUrlWithWbiInternal(bvid, cid, qn)
        } catch (e: HttpException) {
            android.util.Log.e("VideoRepo", "HttpException: ${e.code()}")
            
            //  412 错误时尝试 HTML5 降级方案
            if (e.code() == 412) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " Trying HTML5 fallback for 412 error...")
                return fetchPlayUrlHtml5Fallback(bvid, cid, qn)
            }
            
            if (e.code() in listOf(402, 403, 404)) return null
            throw e
        } catch (e: Exception) { 
            android.util.Log.e("VideoRepo", "Exception: ${e.message}")
            
            //  如果异常消息包含 412，也尝试降级
            if (e.message?.contains("412") == true) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " Trying HTML5 fallback for 412 in exception...")
                return fetchPlayUrlHtml5Fallback(bvid, cid, qn)
            }
            
            return null 
        }
    }
    
    //  [新增] HTML5 降级方案 (无 Referer 鉴权，仅 MP4 格式)
    private suspend fun fetchPlayUrlHtml5Fallback(bvid: String, cid: Long, qn: Int): PlayUrlData? {
        try {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " fetchPlayUrlHtml5Fallback: bvid=$bvid, cid=$cid, qn=$qn")
            
            val (imgKey, subKey) = getWbiKeys()
            
            //  HTML5 参数：platform=html5，fnval=1 (MP4)，high_quality=1
            val params = mapOf(
                "bvid" to bvid, 
                "cid" to cid.toString(), 
                "qn" to qn.toString(),
                "fnval" to "1",  //  MP4 格式
                "fnver" to "0", 
                "fourk" to "1", 
                "platform" to "html5",  //  关键：移除 Referer 鉴权
                "high_quality" to "1",  //  尝试获取 1080p
                "try_look" to "1",
                "gaia_source" to "pre-load",
                "web_location" to "1550101"
            )
            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val response = api.getPlayUrlHtml5(signedParams)
            
            com.android.purebilibili.core.util.Logger.d("VideoRepo", " HTML5 fallback response: code=${response.code}, quality=${response.data?.quality}")
            
            if (response.code == 0 && response.data != null) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", " HTML5 fallback success!")
                return response.data
            }
            
            return null
        } catch (e: Exception) {
            android.util.Log.e("VideoRepo", " HTML5 fallback failed: ${e.message}")
            return null
        }
    }

    /**
     * 获取视频预览图数据 (Videoshot API)
     * 
     * 用于进度条拖动时显示视频缩略图预览
     * @param bvid 视频 BV 号
     * @param cid 视频 CID
     * @return VideoshotData 或 null（如果获取失败）
     */
    suspend fun getVideoshot(bvid: String, cid: Long): VideoshotData? = withContext(Dispatchers.IO) {
        try {
            com.android.purebilibili.core.util.Logger.d("VideoRepo", "🖼️ getVideoshot: bvid=$bvid, cid=$cid")
            val response = api.getVideoshot(bvid = bvid, cid = cid)
            if (response.code == 0 && response.data != null && response.data.isValid) {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", "🖼️ Videoshot success: ${response.data.image.size} images, ${response.data.index.size} frames")
                response.data
            } else {
                com.android.purebilibili.core.util.Logger.d("VideoRepo", "🖼️ Videoshot failed: code=${response.code}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("VideoRepo", "🖼️ Videoshot exception: ${e.message}")
            null
        }
    }
    
    suspend fun getRelatedVideos(bvid: String): List<RelatedVideo> = withContext(Dispatchers.IO) {
        try { api.getRelatedVideos(bvid).data ?: emptyList() } catch (e: Exception) { emptyList() }
    }


    //  [新增] API 错误码分类，提供用户友好的错误提示
    private fun classifyPlayUrlError(code: Int, message: String?): String {
        return when (code) {
            -404 -> "视频不存在或已被删除"
            -403 -> "视频暂不可用"
            -10403 -> {
                when {
                    message?.contains("地区") == true -> "该视频在当前地区不可用"
                    message?.contains("会员") == true || message?.contains("vip") == true -> "需要大会员才能观看"
                    else -> "视频需要特殊权限才能观看"
                }
            }
            -62002 -> "视频已设为私密"
            -62004 -> "视频正在审核中"
            -62012 -> "视频已下架"
            -400 -> "请求参数错误"
            -101 -> "未登录，请先登录"
            -352 -> "请求频率过高，请稍后再试"
            else -> "获取播放地址失败 (错误码: $code)"
        }
    }
}