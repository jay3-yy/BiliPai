package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.network.BuvidManager
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.getSpaceAggregate
import com.android.purebilibili.data.model.response.SpaceLiveRoom
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.CreatorCardStats
import com.android.purebilibili.data.model.response.PopularSeriesPeriod
import com.android.purebilibili.data.model.response.PopularSeriesOneData
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 首页热门/排行榜/每周必看/入站必刷、分区、相关推荐与 UP 主卡片的目录级 API。
 * 原实现位于手机端 VideoRepository（薄封装：API 调用 + toVideoItem 映射），
 * TV 首页与详情页消费同一链路，故下沉共享；手机端原入口保留并委托。
 */
object VideoCatalogRepository {
    private val api = NetworkModule.api
    private data class RelatedKey(val accountScope: String, val bvid: String)
    private val relatedCache = OnDemandRequestCache<RelatedKey, Result<List<RelatedVideo>>>(
        maxEntries = 32,
        maxAgeMs = 45_000L,
        canCache = { it.isSuccess }
    )

    // 只合并正在进行的期数查询；完成后不缓存，下拉刷新仍能获取最新期数。
    private val weeklyPeriodRequests = OnDemandRequestCache<Unit, Result<List<PopularSeriesPeriod>>>(
        maxEntries = 1,
        maxAgeMs = 0L,
    )

    /** 分区 latest 接口出现空数据或错误时，允许用排行榜兜底的一级分区集合。 */
    private val UGC_MAIN_REGION_TIDS = setOf(
        1, 3, 4, 5, 36, 119, 129, 155, 160, 181, 188, 202, 211, 217, 223, 234
    )

    /**
     * The ranking endpoint uses v2 region ids (100x), while the region feed uses
     * the legacy tid ids.  Keep the conversion in one place for the fallback so
     * a main region does not turn into a -400/-404 request (notably 资讯=202).
     */
    private val REGION_TID_TO_RANKING_RID = mapOf(
        1 to 1005,   // 动画
        3 to 1003,   // 音乐
        4 to 1008,   // 游戏
        5 to 1002,   // 娱乐
        36 to 1010,  // 知识
        119 to 1007, // 鬼畜
        129 to 1004, // 舞蹈
        155 to 1014, // 时尚
        160 to 1015, // 生活
        181 to 1001, // 影视
        188 to 1012, // 科技
        202 to 1009, // 资讯
        211 to 1020, // 美食
        217 to 1024, // 动物圈
        223 to 1013, // 汽车
        234 to 1018  // 运动
    )

    fun resolveRegionRankingRid(tid: Int): Int? = REGION_TID_TO_RANKING_RID[tid]

    fun shouldFallbackRegionLatestToRanking(
        tid: Int,
        page: Int,
        latestVideoCount: Int,
        latestResponseCode: Int
    ): Boolean {
        return tid in UGC_MAIN_REGION_TIDS && page == 1 && (latestResponseCode != 0 || latestVideoCount == 0)
    }

    suspend fun getPopularVideos(page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            // 综合热门接口受 buvid3 风控：设备缺 buvid3 时返回非零码且 data 为空，先做 SPI 引导。
            BuvidManager.ensureBuvid3()
            val resp = api.getPopularVideos(pn = page, ps = 30)
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "热门视频加载失败(${resp.code})" }))
            }
            val list = resp.data?.list?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getRankingVideos(rid: Int = 0, type: String = "all"): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            BuvidManager.ensureBuvid3()
            val keys = WbiKeyManager.getWbiKeys().getOrElse { throw it }
            val signedParams = WbiUtils.sign(
                params = mapOf("rid" to rid.toString(), "type" to type),
                imgKey = keys.first,
                subKey = keys.second,
            )
            val resp = api.getRankingVideos(signedParams)
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "排行榜加载失败(${resp.code})" }))
            }
            val list = resp.data?.list
                ?.map { it.toVideoItem() }
                ?.filter { it.bvid.isNotEmpty() }
                ?: emptyList()
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getPreciousVideos(): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            BuvidManager.ensureBuvid3()
            val resp = api.getPopularPreciousVideos()
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "入站必刷加载失败(${resp.code})" }))
            }
            val list = resp.data?.list
                ?.map { it.toVideoItem() }
                ?.filter { it.bvid.isNotEmpty() }
                ?: emptyList()
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getWeeklyPeriods(): Result<List<PopularSeriesPeriod>> = weeklyPeriodRequests.getOrLoad(Unit) {
        dataRequest {
            val response = api.getWeeklySeriesList()
            if (response.code != 0) {
                throw ContentRequestException(response.code, response.message.ifBlank { "每周必看期数加载失败" })
            }
            response.data?.list.orEmpty().filter { it.number > 0 }
                .distinctBy { it.number }.sortedByDescending { it.number }
        }
    }

    suspend fun getWeeklyPeriod(number: Int): Result<PopularSeriesOneData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWeeklySeriesVideos(number)
            val data = response.data
            if (response.code != 0 || data == null) {
                Result.failure(Exception(response.message.ifBlank { "第${number}期加载失败(${response.code})" }))
            } else {
                Result.success(data)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWeeklyMustWatchVideos(number: Int? = null): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            BuvidManager.ensureBuvid3()
            val targetNumber = number ?: run {
                val latest = getWeeklyPeriods().getOrThrow().maxOfOrNull { it.number }
                latest ?: 1
            }
            val resp = api.getWeeklySeriesVideos(number = targetNumber)
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "每周必看加载失败(${resp.code})" }))
            }
            val list = resp.data?.list
                ?.map { it.toVideoItem() }
                ?.filter { it.bvid.isNotEmpty() }
                ?: emptyList()
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getRegionVideos(tid: Int, page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            BuvidManager.ensureBuvid3()
            val resp = api.getRegionVideos(rid = tid, pn = page, ps = 30)
            val list = resp.data?.archives
                ?.map { it.toVideoItem() }
                ?.filter { it.bvid.isNotEmpty() }
                ?: emptyList()
            if (shouldFallbackRegionLatestToRanking(
                    tid = tid,
                    page = page,
                    latestVideoCount = list.size,
                    latestResponseCode = resp.code
                )
            ) {
                if (tid == 202) {
                    val legacy = api.getLegacyRegionVideos(rid = tid, pn = page, ps = 30)
                    if (legacy.code == 0) {
                        return@withContext Result.success(
                            legacy.data?.archives
                                ?.map { it.toVideoItem() }
                                ?.filter { it.bvid.isNotEmpty() }
                                ?: emptyList()
                        )
                    }
                }
                // dynamic/region 只稳定支持子分区；一级分区用排行榜兜底，避免标签页空白。
                val rankingRid = resolveRegionRankingRid(tid)
                if (rankingRid != null) {
                    return@withContext getRankingVideos(rid = rankingRid)
                }
            }
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "分区视频加载失败(${resp.code})" }))
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getRelatedVideos(bvid: String): List<RelatedVideo> =
        getRelatedVideosResult(bvid).getOrDefault(emptyList())

    suspend fun getCachedRelatedVideosResult(bvid: String): Result<List<RelatedVideo>>? {
        TokenManager.awaitRestore()
        val accountScope = recommendationAccountScope()
        val result = relatedCache.peek(RelatedKey(accountScope, bvid))
        return if (accountScope == recommendationAccountScope()) result else null
    }

    suspend fun getRelatedVideosResult(bvid: String): Result<List<RelatedVideo>> = withContext(Dispatchers.IO) {
        TokenManager.awaitRestore()
        val accountScope = recommendationAccountScope()
        val result = relatedCache.getOrLoad(RelatedKey(accountScope, bvid)) {
            dataRequest {
                val response = api.getRelatedVideos(bvid)
                if (response.code != 0 || response.data == null) {
                    error("相关推荐加载失败 (${response.code})")
                }
                check(accountScope == recommendationAccountScope()) { "账号已切换，请重新加载" }
                requireNotNull(response.data)
            }
        }
        if (accountScope == recommendationAccountScope()) result
        else Result.failure(IllegalStateException("账号已切换，请重新加载"))
    }

    fun invalidateRelatedVideos(bvid: String) {
        relatedCache.invalidate(RelatedKey(recommendationAccountScope(), bvid))
    }

    fun clearRelatedVideosCache() {
        relatedCache.clearCompleted()
    }

    private val creatorCardStatsCache = ConcurrentHashMap<Long, CreatorCardStats>()

    private val creatorLiveRoomCache = OnDemandRequestCache<Long, Result<SpaceLiveRoom?>>(
        maxEntries = 32,
        maxAgeMs = 30_000L,
        canCache = { it.isSuccess },
    )

    /** 直播状态短时缓存并合并同一 UP 的并发请求，失败不写入缓存。 */
    suspend fun getCreatorLiveRoom(mid: Long): Result<SpaceLiveRoom?> = withContext(Dispatchers.IO) {
        if (mid <= 0L) return@withContext Result.failure(IllegalArgumentException("Invalid mid"))
        creatorLiveRoomCache.getOrLoad(mid) {
            try {
                val response = NetworkModule.spaceApi.getSpaceAggregate(mid)
                val data = response.data
                if (response.code == 0 && data != null) {
                    Result.success(data.live)
                } else {
                    Result.failure(Exception(response.message.ifBlank { "UP主直播状态加载失败(${response.code})" }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getCachedCreatorCardStats(mid: Long): CreatorCardStats? = creatorCardStatsCache[mid]

    suspend fun getCreatorCardStats(mid: Long): Result<CreatorCardStats> = withContext(Dispatchers.IO) {
        if (mid <= 0L) return@withContext Result.failure(IllegalArgumentException("Invalid mid"))
        creatorCardStatsCache[mid]?.let { return@withContext Result.success(it) }
        try {
            val response = api.getUserCard(mid = mid, photo = false)
            val data = response.data
            if (response.code == 0 && data != null) {
                val stats = CreatorCardStats(
                    followerCount = data.follower.coerceAtLeast(0),
                    videoCount = data.archive_count.coerceAtLeast(0),
                    vipStatus = data.card?.vip?.status ?: 0,
                    vipType = data.card?.vip?.type ?: 0,
                    officialType = data.card?.Official?.type ?: -1,
                    pendantImage = data.card?.pendant?.image.orEmpty(),
                )
                creatorCardStatsCache[mid] = stats
                Result.success(stats)
            } else {
                Result.failure(Exception(response.message.ifBlank { "UP主信息加载失败(${response.code})" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
