// 文件路径: data/repository/BangumiRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.IdUtils
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class BangumiPlayUrlPayload(
    val code: Int = -1,
    val message: String = "",
    val videoInfo: BangumiVideoInfo? = null,
    val hasPlaybackRestriction: Boolean = false
)

internal fun shouldFallbackToLegacyBangumiPlayUrl(payload: BangumiPlayUrlPayload): Boolean {
    if (payload.hasPlaybackRestriction) return false
    if (payload.code == 0) return payload.videoInfo == null
    return payload.code == -400 || payload.code == -404
}

internal fun validateBangumiPlayableVideoInfo(
    videoInfo: BangumiVideoInfo
): Result<BangumiVideoInfo> = when {
    videoInfo.isDrm && !videoInfo.hasPlayableWebStream() -> Result.failure(
        UnsupportedOperationException("该番剧使用 DRM 版权保护，当前版本暂不支持播放")
    )
    else -> Result.success(videoInfo)
}

/**
 * Some PUGV responses keep the DRM marker while still returning a regular DASH/DURL stream.
 * PiliPlus hands those streams to its player, so the marker alone cannot be treated as a hard
 * failure. A response is unsupported only when it has no usable video URL at all.
 */
private fun BangumiVideoInfo.hasPlayableWebStream(): Boolean {
    if (dash?.video.orEmpty().any { it.getValidUrl().isNotBlank() }) return true
    return (durl.orEmpty() + durls.orEmpty()).any { segment ->
        segment.url.isNotBlank() || segment.backupUrl.orEmpty().any { it.isNotBlank() }
    }
}

internal fun buildBangumiPlayUrlParams(
    epId: Long,
    cid: Long,
    qn: Int,
    bvid: String? = null,
    seasonId: Long? = null,
    aid: Long = 0L,
    tryLook: Boolean = true,
    isCourse: Boolean = false
): Map<String, String> {
    val params = linkedMapOf<String, String>()
    if (epId > 0L) {
        params["ep_id"] = epId.toString()
    }
    if (cid > 0L) {
        params["cid"] = cid.toString()
    }
    params["qn"] = qn.toString()
    // For courses/PUGV, fnval=4048 (aligned with PiliPlus), whereas PGC uses 12240
    params["fnval"] = if (isCourse) "4048" else "12240"
    params["fnver"] = "0"
    params["fourk"] = "1"
    params["voice_balance"] = "1"
    params["gaia_source"] = "pre-load"
    params["isGaiaAvoided"] = "true"
    params["web_location"] = "1315873"
    if (seasonId != null && seasonId > 0L) {
        params["season_id"] = seasonId.toString()
    }
    if (tryLook) {
        params["try_look"] = "1"
    }
    val resolvedAid = when {
        aid > 0L -> aid
        !bvid.isNullOrBlank() -> IdUtils.bv2av(bvid)
        else -> 0L
    }
    if (resolvedAid > 0L) {
        params["avid"] = resolvedAid.toString()
    }
    val resolvedBvid = when {
        !bvid.isNullOrBlank() -> bvid
        resolvedAid > 0L -> IdUtils.av2bv(resolvedAid)
        else -> ""
    }
    if (resolvedBvid.isNotBlank()) {
        params["bvid"] = resolvedBvid
    }
    return params
}

internal fun signBangumiPlayUrlParams(
    params: Map<String, String>,
    wbiKeys: Pair<String, String>?,
    includeRiskFingerprint: Boolean = false
): Map<String, String> {
    val (imgKey, subKey) = wbiKeys ?: return params
    if (imgKey.isBlank() || subKey.isBlank()) return params
    return WbiUtils.sign(params, imgKey, subKey, includeRiskFingerprint = includeRiskFingerprint)
}

internal fun decodeBangumiPlayUrlPayload(
    rawJson: String,
    json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
): BangumiPlayUrlPayload {
    val root = json.parseToJsonElement(rawJson).jsonObject
    val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
    val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val resultObject = root["result"] as? JsonObject
    val dataObject = root["data"] as? JsonObject
    // 外层成功并不代表允许播放；保留当前内容的版权和权限限制原因。
    val unavailableReason = (resultObject?.get("not_playable_reason")
        ?: dataObject?.get("not_playable_reason")) as? JsonObject
    val nestedVideoInfo = resultObject?.get("video_info") ?: dataObject?.get("video_info")
    if (code == 0 && nestedVideoInfo !is JsonObject && unavailableReason != null) {
        return BangumiPlayUrlPayload(
            code = unavailableReason["code"]?.jsonPrimitive?.intOrNull ?: -1,
            message = unavailableReason["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            videoInfo = null,
            hasPlaybackRestriction = true
        )
    }
    val videoInfoElement = resultObject?.get("video_info")
        ?: dataObject?.get("video_info")
        ?: dataObject
        ?: resultObject
    val videoInfo = videoInfoElement?.let {
        runCatching {
            json.decodeFromString<BangumiVideoInfo>(it.toString())
        }.onFailure { e ->
            android.util.Log.w("BangumiRepo", "decodeBangumiPlayUrlPayload videoInfo parse failed: ${e.message}")
        }.getOrNull()
    }
    return BangumiPlayUrlPayload(
        code = code,
        message = message,
        videoInfo = videoInfo
    )
}

internal fun shouldLoadBangumiSections(detail: BangumiDetail): Boolean {
    return detail.episodes.isNullOrEmpty() || detail.section == null
}

internal fun mergeBangumiDetailSections(
    detail: BangumiDetail,
    sections: BangumiSectionResult
): BangumiDetail {
    val mainEpisodes = mergeBangumiEpisodes(
        primary = detail.episodes.orEmpty(),
        fallback = sections.mainSection?.episodes.orEmpty()
    )
    val mergedSections = mergeBangumiSections(
        primary = detail.section.orEmpty(),
        fallback = sections.section.orEmpty()
    )
    return detail.copy(
        episodes = mainEpisodes.takeIf { it.isNotEmpty() },
        section = mergedSections.takeIf { it.isNotEmpty() }
    )
}

private fun mergeBangumiEpisodes(
    primary: List<BangumiEpisode>,
    fallback: List<BangumiEpisode>
): List<BangumiEpisode> {
    return (primary + fallback).distinctBy { episode ->
        when {
            episode.id > 0L -> "ep:${episode.id}"
            episode.cid > 0L -> "cid:${episode.cid}"
            episode.bvid.isNotBlank() -> "bvid:${episode.bvid}"
            else -> "title:${episode.title}:${episode.longTitle}"
        }
    }
}

private fun mergeBangumiSections(
    primary: List<BangumiSection>,
    fallback: List<BangumiSection>
): List<BangumiSection> {
    val merged = linkedMapOf<String, BangumiSection>()
    (primary + fallback).forEach { section ->
        val key = when {
            section.id > 0L -> "id:${section.id}"
            section.title.isNotBlank() -> "title:${section.type}:${section.title}"
            else -> "type:${section.type}"
        }
        val previous = merged[key]
        merged[key] = if (previous == null) {
            section
        } else {
            previous.copy(
                title = previous.title.ifBlank { section.title },
                episodes = mergeBangumiEpisodes(
                    primary = previous.episodes.orEmpty(),
                    fallback = section.episodes.orEmpty()
                ).takeIf { it.isNotEmpty() }
            )
        }
    }
    return merged.values.toList()
}

/**
 * 番剧/影视 Repository
 * 处理番剧、电影、电视剧、纪录片等 PGC 内容
 */
object BangumiRepository {
    private val api = NetworkModule.bangumiApi
    
    /**
     * 获取番剧时间表
     * @param type 1=番剧 3=电影 4=国创
     */
    suspend fun getTimeline(
        type: Int = 1,
        before: Int = 3,
        after: Int = 7,
    ): Result<List<TimelineDay>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTimeline(
                types = type,
                before = before.coerceIn(0, 7),
                after = after.coerceIn(0, 7),
            )
            if (response.code == 0 && response.result != null) {
                val checkedResponseResult = requireNotNull(response.result)
                Result.success(checkedResponseResult)
            } else {
                Result.failure(Exception("获取时间表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getTimeline error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 获取番剧索引/列表
     * @param seasonType 1=番剧 2=电影 3=纪录片 4=国创 5=电视剧 7=综艺
     */
    suspend fun getBangumiIndex(
        seasonType: Int = 1,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<BangumiIndexData> = withContext(Dispatchers.IO) {
        try {
            val requestFilter = buildBangumiIndexRequestFilter(
                filter = BangumiFilter(),
                seasonType = seasonType
            )
            val response = api.getBangumiIndex(
                seasonType = seasonType,
                st = seasonType,  //  [修复] st 必须与 seasonType 相同
                page = page,
                pageSize = pageSize,
                order = requestFilter.order,
                sort = requestFilter.sortDirection,
                area = requestFilter.area,
                isFinish = requestFilter.isFinish,
                year = requestFilter.year,
                releaseDate = requestFilter.releaseDate,
                styleId = requestFilter.styleId,
                producerId = requestFilter.producerId,
                seasonStatus = requestFilter.seasonStatus,
                seasonVersion = requestFilter.seasonVersion,
                spokenLanguageType = requestFilter.spokenLanguageType,
                copyright = requestFilter.copyright,
                seasonMonth = requestFilter.seasonMonth
            )
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception("获取番剧列表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiIndex error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 获取番剧详情
     */
    /**
     * 获取番剧详情
     */
    suspend fun getSeasonDetail(seasonId: Long = 0, epId: Long = 0): Result<BangumiDetail> = withContext(Dispatchers.IO) {
        try {
            //  [修复] 使用 ResponseBody 自行解析，避免大型番剧导致 OOM
            // 优先使用 epId (因为历史记录中的 seasonId 可能是 AVID，而 epId 是准确的)，如果 epId 为 0 则使用 seasonId
            val responseBody = if (epId > 0) {
                api.getSeasonDetail(epId = epId)
            } else if (seasonId > 0) {
                api.getSeasonDetail(seasonId = seasonId)
            } else {
                return@withContext Result.failure(Exception("参数错误: seasonId 和 epId 不能同时为空"))
            }

            val jsonString = responseBody.string()
            
            // 使用 kotlinx.serialization.json 手动解析
            val json = kotlinx.serialization.json.Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
            }
            
            val response = json.decodeFromString<BangumiDetailResponse>(jsonString)
            
            if (response.code == 0 && response.result != null) {
                val checkedResponseResult = requireNotNull(response.result)
                val rawDetail = checkedResponseResult
                val resolvedDetail = if (rawDetail.seasonId > 0L && shouldLoadBangumiSections(rawDetail)) {
                    runCatching { api.getSeasonSections(rawDetail.seasonId) }
                        .getOrNull()
                        ?.takeIf { it.code == 0 }
                        ?.result
                        ?.let { mergeBangumiDetailSections(rawDetail, it) }
                        ?: rawDetail
                } else {
                    rawDetail
                }
                //  [调试] 打印追番状态和认证信息
                val userStatus = resolvedDetail.userStatus
                android.util.Log.w("BangumiRepo", """
                     getSeasonDetail 结果:
                    - request seasonId: $seasonId, epId: $epId
                    - result seasonId: ${resolvedDetail.seasonId}
                    - title: ${resolvedDetail.title}
                    - userStatus: $userStatus
                    - follow: ${userStatus?.follow} (1=已追番, 0=未追番)
                    - SESSDATA存在: ${com.android.purebilibili.core.store.TokenManager.sessDataCache?.isNotEmpty() == true}
                """.trimIndent())
                Result.success(resolvedDetail)
            } else {
                // 如果 PGC 接口返回错误（例如 -404 啥都木有），尝试 PUGV 课堂/课程接口
                val pugvResult = getPugvSeasonDetail(seasonId = seasonId, epId = epId)
                if (pugvResult.isSuccess) {
                    return@withContext pugvResult
                }
                Result.failure(Exception("获取番剧详情失败: ${response.message}"))
            }
        } catch (e: OutOfMemoryError) {
            //  [修复] 捕获 OOM 错误，给出更友好的提示
            android.util.Log.e("BangumiRepo", " getSeasonDetail OOM: 番剧数据过大，内存不足", e)
            System.gc() // 尝试触发 GC 回收内存
            Result.failure(Exception("加载失败：番剧数据过大，请稍后重试"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getSeasonDetail error: ${e.message}")
            val pugvResult = getPugvSeasonDetail(seasonId = seasonId, epId = epId)
            if (pugvResult.isSuccess) {
                return@withContext pugvResult
            }
            Result.failure(e)
        }
    }

    /**
     * 获取课堂/课程详情 (PUGV)
     */
    suspend fun getPugvSeasonDetail(seasonId: Long = 0, epId: Long = 0): Result<BangumiDetail> = withContext(Dispatchers.IO) {
        try {
            val responseBody = if (epId > 0) {
                api.getPugvSeasonDetail(epId = epId)
            } else if (seasonId > 0) {
                api.getPugvSeasonDetail(seasonId = seasonId)
            } else {
                return@withContext Result.failure(Exception("参数错误: seasonId 和 epId 不能同时为空"))
            }

            val jsonString = responseBody.string()
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

            val response = json.decodeFromString<com.android.purebilibili.data.model.response.PugvSeasonResponse>(jsonString)
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                val detail = checkedResponseData.toBangumiDetail()
                android.util.Log.w("BangumiRepo", "getPugvSeasonDetail 成功: seasonId=${detail.seasonId}, title=${detail.title}, episodes=${detail.episodes?.size}")
                Result.success(detail)
            } else {
                Result.failure(Exception(response.message.ifBlank { "获取课程详情失败" }))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getPugvSeasonDetail error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getBangumiMediaInfo(mediaId: Long): Result<BangumiMediaInfo> = withContext(Dispatchers.IO) {
        if (mediaId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("mediaId 必须大于 0"))
        }
        runCatching {
            val response = api.getBangumiMediaInfo(mediaId)
            if (response.code != 0) {
                error("获取剧集基本信息失败: ${response.message}")
            }
            response.result?.media ?: error("剧集基本信息为空")
        }
    }

    suspend fun getSeasonSections(seasonId: Long): Result<BangumiSectionResult> = withContext(Dispatchers.IO) {
        if (seasonId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("seasonId 必须大于 0"))
        }
        runCatching {
            val response = api.getSeasonSections(seasonId)
            if (response.code != 0) {
                error("获取剧集分集失败: ${response.message}")
            }
            response.result ?: error("剧集分集为空")
        }
    }

    suspend fun getSeasonDetailByMediaId(mediaId: Long): Result<BangumiDetail> {
        return getBangumiMediaInfo(mediaId).fold(
            onSuccess = { media ->
                if (media.seasonId <= 0L) {
                    Result.failure(IllegalStateException("基本信息未返回 seasonId"))
                } else {
                    getSeasonDetail(seasonId = media.seasonId)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    /**
     * 获取番剧播放地址
     */
    suspend fun getBangumiPlayUrl(
        epId: Long,
        qn: Int = 80,
        cid: Long = 0L,
        bvid: String? = null,
        seasonId: Long? = null,
        aid: Long = 0L,
        isCourse: Boolean = false
    ): Result<BangumiVideoInfo> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BangumiRepo", "📡 getBangumiPlayUrl: epId=$epId, cid=$cid, seasonId=$seasonId, aid=$aid, isCourse=$isCourse, qn=$qn")
            val baseParams = buildBangumiPlayUrlParams(
                epId = epId,
                cid = cid,
                qn = qn,
                bvid = bvid,
                seasonId = seasonId,
                aid = aid,
                isCourse = isCourse
            )
            val wbiKeys = WbiKeyManager.getWbiKeys().getOrNull()
                ?: WbiKeyManager.refreshKeys().getOrNull()
            val signedParams = signBangumiPlayUrlParams(
                params = baseParams,
                wbiKeys = wbiKeys,
                includeRiskFingerprint = isCourse
            )
            android.util.Log.d(
                "BangumiRepo",
                "📡 getBangumiPlayUrl request params: wbiSigned=${signedParams.containsKey("w_rid")}, keys=${signedParams.keys.sorted()}"
            )
            val playbackApi = NetworkModule.playbackBangumiApi()

            val finalResponse = if (isCourse) {
                // 课程优先使用 PUGV playurl
                val pugvResponse = runCatching {
                    val pugvRawJson = playbackApi.getPugvPlayUrl(signedParams).string()
                    decodeBangumiPlayUrlPayload(pugvRawJson)
                }.getOrNull()
                if (pugvResponse != null && pugvResponse.code == 0 && pugvResponse.videoInfo != null) {
                    pugvResponse
                } else {
                    val primary = runCatching {
                        decodeBangumiPlayUrlPayload(playbackApi.getBangumiPlayUrl(signedParams).string())
                    }.getOrNull()
                    primary?.takeIf { it.code == 0 && it.videoInfo != null }
                        ?: pugvResponse
                        ?: BangumiPlayUrlPayload(code = -1, message = "获取播放地址失败", videoInfo = null)
                }
            } else {
                val primaryResponse = runCatching {
                    decodeBangumiPlayUrlPayload(
                        rawJson = playbackApi.getBangumiPlayUrl(
                            signedParams
                        ).string()
                    )
                }.getOrNull()
                val response = if (primaryResponse != null && !shouldFallbackToLegacyBangumiPlayUrl(primaryResponse)) {
                    primaryResponse
                } else {
                    runCatching {
                        decodeBangumiPlayUrlPayload(
                            rawJson = playbackApi.getBangumiPlayUrlLegacy(
                                signedParams
                            ).string()
                        )
                    }.getOrNull() ?: primaryResponse
                }
                // 如果常规 PGC playurl 失败（例如 -404 啥都木有，或异常），尝试 PUGV 课堂/课程 playurl
                if (response != null && (response.hasPlaybackRestriction ||
                        (response.code == 0 && response.videoInfo != null))) {
                    response
                } else {
                    runCatching {
                        val pugvRawJson = playbackApi.getPugvPlayUrl(signedParams).string()
                        decodeBangumiPlayUrlPayload(pugvRawJson)
                    }.getOrNull()?.takeIf { it.code == 0 && it.videoInfo != null }
                        ?: response
                        ?: BangumiPlayUrlPayload(code = -1, message = "获取播放地址失败", videoInfo = null)
                }
            }
            android.util.Log.d(
                "BangumiRepo",
                "📡 getBangumiPlayUrl response: code=${finalResponse.code}, msg=${finalResponse.message}, hasResult=${finalResponse.videoInfo != null}"
            )
            
            if (finalResponse.code == 0 && finalResponse.videoInfo != null) {
                val result = finalResponse.videoInfo
                android.util.Log.d(
                    "BangumiRepo",
                    "📹 PlayUrl: quality=${result.quality}, hasDash=${result.dash != null}, " +
                        "hasDurl=${!result.durl.isNullOrEmpty()}, preview=${result.isPreview}, " +
                        "paid=${result.hasPaid}, drm=${result.isDrm}, status=${result.status}"
                )
                validateBangumiPlayableVideoInfo(result)
            } else {
                val errorMsg = if (finalResponse.hasPlaybackRestriction) {
                    finalResponse.message.ifBlank { "当前内容暂不可播放" }
                } else when (finalResponse.code) {
                    -10403 -> finalResponse.message.ifBlank { "当前内容暂不可播放，可能受地区或会员权限限制" }
                    -404 -> "视频或课程不存在"
                    -101 -> "请先登录后观看"
                    -400 -> "请求参数错误"
                    -403 -> finalResponse.message.ifBlank {
                        if (isCourse) "访问权限不足：该课程需购买后观看" else "访问权限不足"
                    }
                    else -> "获取播放地址失败: ${finalResponse.message} (code=${finalResponse.code})"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiPlayUrl error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 追番/追剧/收藏课程
     */
    suspend fun followBangumi(seasonId: Long, isCourse: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
            android.util.Log.w("BangumiRepo", "📌 追番/收藏请求: seasonId=$seasonId, isCourse=$isCourse, csrf=${csrf.take(10)}...")
            if (isCourse) {
                val pugvResponse = runCatching { api.addFavPugv(seasonId = seasonId, csrf = csrf) }.getOrNull()
                return@withContext if (pugvResponse?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(pugvResponse?.message?.ifBlank { "收藏课程失败" } ?: "收藏课程失败"))
                }
            }
            val response = api.followBangumi(seasonId = seasonId, csrf = csrf)
            android.util.Log.w("BangumiRepo", "📌 追番响应: code=${response.code}, message=${response.message}")
            if (response.code == 0) {
                Result.success(true)
            } else {
                // 如果常规追番失败，尝试课程收藏接口
                val pugvResponse = runCatching { api.addFavPugv(seasonId = seasonId, csrf = csrf) }.getOrNull()
                if (pugvResponse?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("追番/收藏失败: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "followBangumi error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 取消追番/追剧/取消收藏课程
     */
    suspend fun unfollowBangumi(seasonId: Long, isCourse: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
            if (isCourse) {
                val pugvResponse = runCatching { api.delFavPugv(seasonId = seasonId, csrf = csrf) }.getOrNull()
                return@withContext if (pugvResponse?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(pugvResponse?.message?.ifBlank { "取消收藏失败" } ?: "取消收藏失败"))
                }
            }
            val response = api.unfollowBangumi(seasonId = seasonId, csrf = csrf)
            if (response.code == 0) {
                Result.success(true)
            } else {
                // 如果常规取消追番失败，尝试课程取消收藏接口
                val pugvResponse = runCatching { api.delFavPugv(seasonId = seasonId, csrf = csrf) }.getOrNull()
                if (pugvResponse?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("取消追番/收藏失败: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "unfollowBangumi error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 更新追番/追剧状态：1=想看，2=在看，3=看过
     */
    suspend fun updateBangumiFollowStatus(
        seasonId: Long,
        status: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
            val response = api.updateBangumiFollowStatus(
                seasonId = seasonId,
                status = status,
                csrf = csrf
            )
            if (response.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception("更新追番状态失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "updateBangumiFollowStatus error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateBangumiFollowStatuses(
        seasonIds: Collection<Long>,
        status: Int,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val normalizedIds = seasonIds.asSequence().filter { it > 0L }.distinct().toList()
        if (normalizedIds.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("请选择要更新的番剧"))
        }
        try {
            val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
            val response = api.updateBangumiFollowStatusBatch(
                seasonIds = normalizedIds.joinToString(","),
                status = status,
                csrf = csrf,
            )
            if (response.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception("批量更新追番状态失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "updateBangumiFollowStatuses error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getBangumiIndexConditions(
        seasonType: Int? = null,
        indexType: Int? = null,
        type: Int = 0,
    ): Result<BangumiIndexConditionData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBangumiIndexCondition(
                seasonType = seasonType,
                type = type,
                indexType = indexType,
            )
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception("获取番剧索引条件失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiIndexConditions error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getBangumiIndexPage(
        seasonType: Int? = null,
        indexType: Int? = null,
        type: Int = 0,
        page: Int = 1,
        pageSize: Int = 30,
        params: Map<String, String> = emptyMap(),
    ): Result<BangumiIndexData> = withContext(Dispatchers.IO) {
        try {
            val query = params.toMutableMap().apply {
                put("type", type.toString())
                put("page", page.coerceAtLeast(1).toString())
                put("pagesize", pageSize.coerceAtLeast(1).toString())
                seasonType?.let {
                    put("season_type", it.toString())
                    putIfAbsent("st", it.toString())
                }
                indexType?.let { put("index_type", it.toString()) }
            }
            val response = api.getBangumiIndexResult(query)
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception("获取番剧索引失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiIndexPage error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 获取番剧索引/列表（支持筛选）
     */
    suspend fun getBangumiIndexWithFilter(
        seasonType: Int = 1,
        page: Int = 1,
        pageSize: Int = 20,
        filter: BangumiFilter = BangumiFilter()
    ): Result<BangumiIndexData> = withContext(Dispatchers.IO) {
        try {
            val requestFilter = buildBangumiIndexRequestFilter(
                filter = filter,
                seasonType = seasonType
            )
            val response = api.getBangumiIndex(
                seasonType = seasonType,
                st = seasonType,
                page = page,
                pageSize = pageSize,
                order = requestFilter.order,
                sort = requestFilter.sortDirection,
                area = requestFilter.area,
                isFinish = requestFilter.isFinish,
                year = requestFilter.year,
                releaseDate = requestFilter.releaseDate,
                styleId = requestFilter.styleId,
                producerId = requestFilter.producerId,
                seasonStatus = requestFilter.seasonStatus,
                seasonVersion = requestFilter.seasonVersion,
                spokenLanguageType = requestFilter.spokenLanguageType,
                copyright = requestFilter.copyright,
                seasonMonth = requestFilter.seasonMonth
            )
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception("获取番剧列表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiIndexWithFilter error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 搜索番剧
     */
    suspend fun searchBangumi(
        keyword: String,
        seasonType: Int = BangumiType.ANIME.value,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<BangumiSearchData> = withContext(Dispatchers.IO) {
        try {
            val navApi = NetworkModule.api
            val searchApi = NetworkModule.searchApi
            val searchType = resolveBangumiSearchTypeForSeasonType(seasonType)
            
            // 获取 WBI 密钥
            val navResp = navApi.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            val imgKey = wbiImg?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            val subKey = wbiImg?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            
            val params = mutableMapOf(
                "keyword" to keyword,
                "search_type" to searchType.value,
                "page" to page.toString(),
                "pagesize" to pageSize.toString()
            )
            
            // WBI 签名
            val signedParams = if (imgKey.isNotEmpty()) WbiUtils.sign(params, imgKey, subKey) else params
            val response = if (searchType == SearchType.MEDIA_FT) {
                searchApi.searchMediaFt(signedParams)
            } else {
                searchApi.searchBangumi(signedParams)
            }
            
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception("搜索番剧失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "searchBangumi error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 获取我的追番列表
     */
    suspend fun getMyFollowBangumi(
        type: Int = 1,  // 1=追番 2=追剧
        followStatus: Int? = null,
        page: Int = 1,
        pageSize: Int = 30,
        vmid: Long? = null
    ): Result<MyFollowBangumiData> = withContext(Dispatchers.IO) {
        try {
            val mid = vmid?.takeIf { it > 0L } ?: TokenManager.midCache ?: return@withContext Result.failure(Exception("未登录"))
            val response = api.getMyFollowBangumi(
                vmid = mid,
                type = type,
                followStatus = followStatus,
                pn = page,
                ps = pageSize
            )
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception("获取追番列表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getMyFollowBangumi error: ${e.message}")
            Result.failure(e)
        }
    }
}
