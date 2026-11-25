// 文件路径: data/repository/DanmakuRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.DanmakuThumbupStatsItem
import com.android.purebilibili.data.model.response.GradeDanmakuSummary
import com.android.purebilibili.data.model.response.parseGradeDanmakuSummary
import com.android.purebilibili.danmaku.parser.DanmakuProto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.abs

data class DanmakuCloudFilterRule(
    val id: Long,
    val type: Int,
    val filter: String
)

data class DanmakuCloudFilterRules(
    val rules: List<DanmakuCloudFilterRule>,
    val toast: String? = null
)

internal data class DanmakuThumbupState(
    val likes: Int,
    val liked: Boolean
)

/** PiliPlus 将重复操作响应作为服务端真实状态，用于校正本地开关。 */
internal fun resolveDanmakuLikeResult(code: Int, requestedLike: Boolean): Boolean? = when (code) {
    0 -> requestedLike
    65006 -> true
    65004 -> false
    else -> null
}

internal fun resolveDanmakuThumbupState(
    dmid: Long,
    data: Map<String, DanmakuThumbupStatsItem>
): DanmakuThumbupState? {
    val key = dmid.toString()
    val matched = data[key] ?: return null
    return DanmakuThumbupState(
        likes = matched.likes.coerceAtLeast(0),
        liked = matched.userLike == 1
    )
}

internal fun mapSendDanmakuErrorMessage(code: Int, fallbackMessage: String): String {
    return when (code) {
        -101 -> "请先登录"
        -102 -> "账号被封禁"
        -111 -> "鉴权失败，请重新登录"
        -400 -> "请求参数错误"
        -509 -> "请求过于频繁，请稍后再试"
        36700 -> "系统升级中，请稍后再试"
        36701 -> "弹幕包含被禁止的内容"
        36702 -> "弹幕长度超出限制"
        36703 -> "发送频率过快，请稍后再试"
        36704 -> "当前视频暂不允许发送弹幕"
        36705 -> "当前账号等级不足，无法发送该弹幕"
        36706 -> "当前账号等级不足，无法发送顶端弹幕"
        36707 -> "当前账号等级不足，无法发送底端弹幕"
        36708 -> "当前账号暂无彩色弹幕权限"
        36709 -> "当前账号等级不足，无法发送高级弹幕"
        36710 -> "当前账号暂无该弹幕样式权限"
        36711 -> "该视频禁止发送弹幕"
        36712 -> "当前账号等级限制，弹幕长度上限更低"
        36718 -> "当前账号不是大会员，无法发送渐变彩色弹幕"
        else -> fallbackMessage.ifEmpty { "发送弹幕失败 ($code)" }
    }
}

internal const val DANMAKU_VIP_GRADUAL_COLOR_CODE = 60001
internal const val DANMAKU_UP_IDENTITY_CHECKBOX_TYPE = 4

internal data class DanmakuPostPayload(
    val aid: Long,
    val cid: Long,
    val message: String,
    val progress: Long,
    val color: Int,
    val fontSize: Int,
    val mode: Int,
    val colorful: Int?,
    val checkboxType: Int?
)

internal data class AttentionCommandDanmakuPayload(
    val aid: Long,
    val cid: Long,
    val progress: Long,
    val type: Int,
    val plat: Int,
    val data: String
)

internal fun buildDanmakuPostPayload(
    aid: Long,
    cid: Long,
    message: String,
    progress: Long,
    color: Int,
    fontSize: Int,
    mode: Int,
    colorful: Boolean,
    upIdentity: Boolean
): DanmakuPostPayload {
    return DanmakuPostPayload(
        aid = aid,
        cid = cid,
        message = message,
        progress = progress,
        color = if (colorful) 16777215 else color,
        fontSize = fontSize,
        mode = mode,
        colorful = DANMAKU_VIP_GRADUAL_COLOR_CODE.takeIf { colorful },
        checkboxType = DANMAKU_UP_IDENTITY_CHECKBOX_TYPE.takeIf { upIdentity }
    )
}

internal fun buildAttentionCommandDanmakuPayload(
    aid: Long,
    cid: Long,
    progress: Long,
    durationMs: Long,
    posX: Int,
    posY: Int
): AttentionCommandDanmakuPayload {
    val duration = durationMs.coerceAtLeast(1000L)
    val safeX = posX.coerceIn(118, 549)
    val safeY = posY.coerceIn(82, 293)
    return AttentionCommandDanmakuPayload(
        aid = aid,
        cid = cid,
        progress = progress.coerceAtLeast(0L),
        type = 5,
        plat = 1,
        data = """{"duration":$duration,"posX":$safeX,"posY":$safeY}"""
    )
}

internal fun resolveDanmakuSegmentCount(
    durationMs: Long,
    metadataSegmentCount: Int?
): Int = DanmakuContentRepository.resolveSegmentCount(durationMs, metadataSegmentCount)

internal fun resolveGradeDanmakuSummary(
    commands: List<DanmakuProto.CommandDm>,
    gradeId: String
): GradeDanmakuSummary? {
    for (command in commands) {
        if (!command.command.trim().equals("#GRADE#", ignoreCase = true)) continue
        val extra = try {
            Json.parseToJsonElement(command.extra) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        } ?: continue
        if ((extra["grade_id"] as? JsonPrimitive)?.contentOrNull == gradeId) {
            return parseGradeDanmakuSummary(extra)
        }
    }
    return null
}

/**
 * 弹幕相关数据仓库
 * 从 VideoRepository 拆分出来，专注于弹幕功能
 */
object DanmakuRepository {
    private val api = NetworkModule.api
    private data class DanmakuViewKey(
        val accountScope: String,
        val cid: Long,
        val aid: Long,
    )
    private val danmakuViewCache = OnDemandRequestCache<DanmakuViewKey, DanmakuProto.DmWebViewReply?>(
        maxEntries = 16,
        maxAgeMs = 15_000L,
        canCache = { it != null },
    )

    /**
     * 清除弹幕缓存。
     * 读取与缓存的实现已下沉到共享 :core-data 的 [DanmakuContentRepository]，
     * 保留委托以稳定手机端调用方。
     */
    fun clearDanmakuCache() {
        danmakuViewCache.clearCompleted()
        DanmakuContentRepository.clearCache()
    }

    fun getDanmakuCacheStats(): DanmakuCacheStats = DanmakuContentRepository.getDanmakuCacheStats()

    /**
     * 获取 XML 格式弹幕原始数据（旧版 API，后备路径）
     */
    suspend fun getDanmakuRawData(cid: Long): ByteArray? =
        DanmakuContentRepository.getDanmakuRawData(cid)

    /**
     * 获取弹幕元数据 (High-Energy, Command Dms, etc.)
     */
    suspend fun getDanmakuView(cid: Long, aid: Long): com.android.purebilibili.danmaku.parser.DanmakuProto.DmWebViewReply? = withContext(Dispatchers.IO) {
        if (cid <= 0L) return@withContext null
        try {
            com.android.purebilibili.core.store.TokenManager.awaitRestore()
            val key = DanmakuViewKey(
                accountScope = videoMetadataAccountScope(),
                cid = cid,
                aid = aid,
            )
            val reply = danmakuViewCache.getOrLoad(key) {
                try {
                    com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "🎯 getDanmakuView: cid=$cid, aid=$aid")
                    val responseBody = api.getDanmakuView(oid = cid, pid = aid)
                    val bytes = responseBody.bytes()
                    if (key.accountScope != videoMetadataAccountScope()) {
                        null
                    } else if (bytes.isNotEmpty()) {
                        val result = com.android.purebilibili.danmaku.parser.DanmakuParser.parseWebViewReply(bytes)
                        com.android.purebilibili.core.util.Logger.d("DanmakuRepo", " Metadata parsed: count=${result.count}, special=${result.specialDms.size}, command=${result.commandDms.size}")
                        result
                    } else {
                        null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("DanmakuRepo", " getDanmakuView failed: ${e.message}")
                    null
                }
            }
            if (key.accountScope == videoMetadataAccountScope()) reply else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", " getDanmakuView failed: ${e.message}")
            null
        }
    }

    /** Reload authenticated command metadata; grade/post does not return aggregate statistics. */
    suspend fun getGradeDanmakuSummary(
        cid: Long,
        aid: Long,
        gradeId: String
    ): Result<GradeDanmakuSummary> = withContext(Dispatchers.IO) {
        try {
            val bytes = api.getDanmakuView(oid = cid, pid = aid).bytes()
            if (bytes.isEmpty()) {
                return@withContext Result.failure(Exception("打分统计暂不可用"))
            }
            val metadata = com.android.purebilibili.danmaku.parser.DanmakuParser.parseWebViewReply(bytes)
            val summary = resolveGradeDanmakuSummary(metadata.commandDms, gradeId)
                ?: return@withContext Result.failure(Exception("未找到打分统计"))
            Result.success(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取 Protobuf 格式弹幕单段（每段 6 分钟，下标从 1 开始）
     */
    suspend fun getDanmakuSegment(
        cid: Long,
        segmentIndex: Int
    ): ByteArray? = DanmakuContentRepository.getDanmakuSegment(cid, segmentIndex)

    /** UP主关闭弹幕的 cid 集合（来自 DmSegMobileReply.state == 1） */
    private val serverDisabledDanmakuCids =
        java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    fun markDanmakuServerDisabled(cid: Long) {
        serverDisabledDanmakuCids.add(cid)
    }

    fun isDanmakuServerDisabled(cid: Long): Boolean = cid in serverDisabledDanmakuCids

    /** 拉取云端弹幕屏蔽规则（关键词/正则/UID），未登录返回失败 */
    suspend fun getDanmakuCloudFilterRules(): Result<DanmakuCloudFilterRules> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getDanmakuFilterRules()
                if (response.code != 0) {
                    return@withContext Result.failure(Exception(response.message.ifEmpty { "同步云端弹幕屏蔽规则失败" }))
                }
                val data = response.data
                    ?: return@withContext Result.failure(Exception("同步云端弹幕屏蔽规则失败"))
                val rules = buildList {
                    addAll(data.rule)
                    addAll(data.rule1)
                    addAll(data.rule2)
                }.map { DanmakuCloudFilterRule(id = it.id, type = it.type, filter = it.filter) }
                Result.success(DanmakuCloudFilterRules(rules = rules, toast = data.toast))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 添加云端弹幕屏蔽规则（type: 0=关键词, 1=正则, 2=UID crc32 hex） */
    suspend fun addDanmakuCloudFilterRule(type: Int, filter: String): Result<DanmakuCloudFilterRule> =
        withContext(Dispatchers.IO) {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                val response = api.addDanmakuFilterRule(type = type, filter = filter, csrf = csrf)
                if (response.code != 0) {
                    return@withContext Result.failure(Exception(response.message.ifEmpty { "添加云端弹幕屏蔽规则失败" }))
                }
                val data = response.data
                    ?: return@withContext Result.failure(Exception("添加云端弹幕屏蔽规则失败"))
                Result.success(
                    DanmakuCloudFilterRule(id = data.id, type = data.type, filter = data.filter)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 删除云端弹幕屏蔽规则 */
    suspend fun deleteDanmakuCloudFilterRule(id: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                val response = api.deleteDanmakuFilterRule(ids = id, csrf = csrf)
                if (response.code != 0) {
                    Result.failure(Exception(response.message.ifEmpty { "删除云端弹幕屏蔽规则失败" }))
                } else {
                    Result.success(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 并发拉取整支视频的所有分段。
     * Full-video loading is retained only for offline asset export. Playback uses single segments.
     */
    suspend fun getDanmakuSegments(
        cid: Long,
        durationMs: Long,
        metadataSegmentCount: Int? = null
    ): List<ByteArray> =
        DanmakuContentRepository.getDanmakuSegments(cid, durationMs, metadataSegmentCount)

    /** Full export is user-requested offline download, not the playback loading path. */
    suspend fun downloadSpecialDanmaku(url: String, destination: File): Long? = withContext(Dispatchers.IO) {
        val resolvedUrl = if (url.startsWith("//")) "https:$url" else url
        try {
            api.getDanmakuSpecialDm(resolvedUrl).use { body ->
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            bytes += count
                        }
                        bytes
                    }
                }
            }
        } catch (e: CancellationException) {
            destination.delete()
            throw e
        } catch (e: Exception) {
            destination.delete()
            android.util.Log.w("DanmakuRepo", "Special danmaku export failed: ${e.message}")
            null
        }
    }

    suspend fun getWebMask(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val resolvedUrl = if (url.startsWith("//")) "https:$url" else url
        try {
            api.getDanmakuSpecialDm(resolvedUrl).bytes().takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DanmakuRepo", "Webmask fetch failed: ${e.message}")
            null
        }
    }
    
    /**
     * 发送弹幕
     * 
     * @param aid 视频 aid (必需)
     * @param cid 视频 cid (必需)
     * @param message 弹幕内容 (最多 100 字)
     * @param progress 弹幕出现时间 (毫秒)
     * @param color 弹幕颜色 (十进制 RGB，默认白色 16777215)
     * @param fontSize 字号: 18=小, 25=中(默认), 36=大
     * @param mode 模式: 1=滚动(默认), 4=底部, 5=顶部
     * @return 发送结果，包含弹幕 ID
     */
    suspend fun sendDanmaku(
        aid: Long,
        cid: Long,
        message: String,
        progress: Long,
        color: Int = 16777215,
        fontSize: Int = 25,
        mode: Int = 1,
        colorful: Boolean = false,
        upIdentity: Boolean = false
    ): Result<com.android.purebilibili.data.model.response.SendDanmakuData> = withContext(Dispatchers.IO) {
        try {
            // 验证登录状态
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            // 验证弹幕内容
            if (message.isBlank()) {
                return@withContext Result.failure(Exception("弹幕内容不能为空"))
            }
            if (message.length > 100) {
                return@withContext Result.failure(Exception("弹幕内容过长，最多 100 字"))
            }
            
            com.android.purebilibili.core.util.Logger.d(
                "DanmakuRepo",
                "📤 sendDanmaku: aid=$aid, cid=$cid, msg=$message, progress=${progress}ms, color=$color, mode=$mode, colorful=$colorful, upIdentity=$upIdentity"
            )
            val payload = buildDanmakuPostPayload(
                aid = aid,
                cid = cid,
                message = message,
                progress = progress,
                color = color,
                fontSize = fontSize,
                mode = mode,
                colorful = colorful,
                upIdentity = upIdentity
            )
            val response = api.sendDanmaku(
                oid = payload.cid,
                aid = payload.aid,
                msg = payload.message,
                progress = payload.progress,
                color = payload.color,
                fontsize = payload.fontSize,
                mode = payload.mode,
                colorful = payload.colorful,
                checkboxType = payload.checkboxType,
                csrf = csrf
            )
            
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "✅ Danmaku sent: dmid=${checkedResponseData.dmid_str}")
                Result.success(checkedResponseData)
            } else {
                val errorMsg = mapSendDanmakuErrorMessage(response.code, response.message)
                android.util.Log.e("DanmakuRepo", "❌ sendDanmaku failed: ${response.code} - ${response.message}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "❌ sendDanmaku exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendAttentionCommandDanmaku(
        aid: Long,
        cid: Long,
        progress: Long,
        durationMs: Long = 6000L,
        posX: Int = 240,
        posY: Int = 160
    ): Result<com.android.purebilibili.data.model.response.CommandDanmakuData> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            val payload = buildAttentionCommandDanmakuPayload(
                aid = aid,
                cid = cid,
                progress = progress,
                durationMs = durationMs,
                posX = posX,
                posY = posY
            )
            val response = api.sendCommandDanmaku(
                type = payload.type,
                aid = payload.aid,
                cid = payload.cid,
                progress = payload.progress,
                plat = payload.plat,
                data = payload.data,
                csrf = csrf
            )
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception(mapSendDanmakuErrorMessage(response.code, response.message)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "❌ sendAttentionCommandDanmaku exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 提交打分弹幕 (x/v2/dm/command/grade/post)
     *
     * gradeScore 为偶数，最大 10。成功响应只确认个人提交，不含聚合统计。
     *
     * @param aid 稿件 aid
     * @param cid 分P cid
     * @param progress 弹幕出现时间 (毫秒)
     * @param gradeId 打分 ID (grade_id)
     * @param gradeScore 分数 (偶数 2~10)
     */
    suspend fun submitGradeDanmaku(
        aid: Long,
        cid: Long,
        progress: Long,
        gradeId: String,
        gradeScore: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            val id = gradeId.toLongOrNull()
            if (id == null) {
                return@withContext Result.failure(Exception("缺少打分 ID"))
            }
            val response = api.gradeDanmaku(
                aid = aid,
                cid = cid,
                progress = progress,
                gradeId = id,
                gradeScore = gradeScore,
                csrf = csrf
            )
            if (response.code == 0) {
                danmakuViewCache.invalidateWhere { it.cid == cid && it.aid == aid }
                com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "✅ Grade danmaku submitted: grade_id=$gradeId score=$gradeScore")
                Result.success(Unit)
            } else {
                android.util.Log.e("DanmakuRepo", "❌ gradeDanmaku failed: ${response.code} - ${response.message}")
                Result.failure(Exception(mapSendDanmakuErrorMessage(response.code, response.message)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "❌ submitGradeDanmaku exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 撤回弹幕
     * 
     * 仅能撤回自己 2 分钟内的弹幕，每天 3 次机会
     * 
     * @param cid 视频 cid
     * @param dmid 弹幕 ID
     * @return 撤回结果 (message 包含剩余次数)
     */
    suspend fun recallDanmaku(
        cid: Long,
        dmid: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "📤 recallDanmaku: cid=$cid, dmid=$dmid")
            
            val response = api.recallDanmaku(cid = cid, dmid = dmid, csrf = csrf)
            
            if (response.code == 0) {
                com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "✅ Danmaku recalled: ${response.message}")
                Result.success(response.message)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -111 -> "鉴权失败，请重新登录"
                    -400 -> "请求参数错误"
                    36301 -> "撤回次数已用完" 
                    36302 -> "弹幕发送超过2分钟，无法撤回"
                    36303 -> "该弹幕无法撤回"
                    else -> response.message.ifEmpty { "撤回失败 (${response.code})" }
                }
                android.util.Log.e("DanmakuRepo", "❌ recallDanmaku failed: ${response.code} - ${response.message}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "❌ recallDanmaku exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 查询单条弹幕的点赞状态与票数
     */
    internal suspend fun getDanmakuThumbupState(
        cid: Long,
        dmid: Long
    ): Result<DanmakuThumbupState> = withContext(Dispatchers.IO) {
        try {
            if (dmid <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("弹幕ID无效"))
            }

            val response = api.getDanmakuThumbupStats(
                oid = cid,
                ids = dmid.toString()
            )

            if (response.code != 0) {
                val message = response.message.ifEmpty { "查询弹幕投票状态失败 (${response.code})" }
                return@withContext Result.failure(Exception(message))
            }

            val state = resolveDanmakuThumbupState(dmid = dmid, data = response.data)
                ?: return@withContext Result.failure(Exception("未找到该弹幕投票信息"))

            Result.success(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "❌ getDanmakuThumbupState exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 点赞弹幕
     * 
     * @param cid 视频 cid
     * @param dmid 弹幕 ID
     * @param like true=点赞, false=取消点赞
     * @return 服务端确认的已赞状态；重复操作也会返回真实状态。
     */
    suspend fun likeDanmaku(
        cid: Long,
        dmid: Long,
        like: Boolean = true
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val op = if (like) 1 else 2
            com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "📤 likeDanmaku: cid=$cid, dmid=$dmid, op=$op")
            
            val response = api.likeDanmaku(oid = cid, dmid = dmid, op = op, csrf = csrf)
            
            val confirmedLike = resolveDanmakuLikeResult(response.code, like)
            if (confirmedLike != null) {
                com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "✅ Danmaku ${if (confirmedLike) "liked" else "unliked"}")
                Result.success(confirmedLike)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -111 -> "鉴权失败，请重新登录"
                    -400 -> "请求参数错误"
                    else -> response.message.ifEmpty { "操作失败 (${response.code})" }
                }
                android.util.Log.e("DanmakuRepo", "❌ likeDanmaku failed: ${response.code} - ${response.message}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "❌ likeDanmaku exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 举报弹幕
     * 
     * @param cid 视频 cid
     * @param dmid 弹幕 ID
     * @param reason 举报原因: 1=违法/2=色情/3=广告/4=引战/5=辱骂/6=剧透/7=刷屏/8=其他
     * @param content 举报描述 (可选)
     */
    suspend fun reportDanmaku(
        cid: Long,
        dmid: Long,
        reason: Int,
        content: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "📤 reportDanmaku: cid=$cid, dmid=$dmid, reason=$reason")
            
            val response = api.reportDanmaku(cid = cid, dmid = dmid, reason = reason, content = content, csrf = csrf)
            
            if (response.code == 0) {
                com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "✅ Danmaku reported")
                Result.success(Unit)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -111 -> "鉴权失败，请重新登录"
                    -400 -> "请求参数错误"
                    else -> response.message.ifEmpty { "举报失败 (${response.code})" }
                }
                android.util.Log.e("DanmakuRepo", "❌ reportDanmaku failed: ${response.code} - ${response.message}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "❌ reportDanmaku exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 启动直播弹幕连接
     * 
     * @param scope 用于管理 WebSocket 生命周期的协程作用域 (通常是 ViewModelScope)
     * @param roomId 直播间 ID
     * @return 连接成功的 Client 实例
     */
    suspend fun startLiveDanmaku(
        scope: kotlinx.coroutines.CoroutineScope,
        roomId: Long
    ): Result<com.android.purebilibili.core.network.socket.LiveDanmakuClient> = withContext(Dispatchers.IO) {
        try {
            com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "📡 Getting live danmaku info for room=$roomId...")

            // 1) 确保 buvid3 已初始化（getDanmuInfo 从 2025-06 起要求 buvid3）
            VideoRepository.ensureBuvid3()

            // 2) 统一解析真实房间号（避免短号导致弹幕 token 或房间参数不一致）
            val realRoomId = LiveRepository.resolveRealRoomId(roomId)

            // 3) 强制使用 WBI 签名请求 getDanmuInfo，不再回退无签名
            val initialWbiKeys = com.android.purebilibili.core.network.WbiKeyManager.getWbiKeys().getOrNull()
                ?: com.android.purebilibili.core.network.WbiKeyManager.refreshKeys().getOrNull()
                ?: return@withContext Result.failure(Exception("获取 WBI 密钥失败，无法连接直播弹幕"))

            fun buildSignedParams(keys: Pair<String, String>): Map<String, String> {
                val params = mapOf(
                    "id" to realRoomId.toString(),
                    "type" to "0",
                    "web_location" to "444.8"
                )
                return com.android.purebilibili.core.network.WbiUtils.sign(params, keys.first, keys.second)
            }

            var response = api.getDanmuInfoWbi(buildSignedParams(initialWbiKeys))
            if (response.code != 0) {
                // WBI 相关失败时，主动刷新密钥再重试一次
                com.android.purebilibili.core.network.WbiKeyManager.invalidateCache()
                val refreshedKeys = com.android.purebilibili.core.network.WbiKeyManager.refreshKeys().getOrNull()
                if (refreshedKeys != null) {
                    response = api.getDanmuInfoWbi(buildSignedParams(refreshedKeys))
                }
            }

            val info = response.data
            if (response.code != 0 || info == null) {
                return@withContext Result.failure(Exception("获取弹幕服务信息失败: ${response.code} (msg=${response.message})"))
            }
            
            val token = info.token
            val hosts = info.host_list
            
            if (hosts.isEmpty()) {
                return@withContext Result.failure(Exception("无可用弹幕服务器"))
            }
            
            // Try the secure 443 endpoint first, then the remaining secure endpoints and
            // finally plain WebSocket endpoints returned by the live service.
            val orderedHosts = hosts.sortedWith(
                compareBy<com.android.purebilibili.data.model.response.LiveDanmuHost> {
                    when {
                        it.wss_port == 443 -> 0
                        it.wss_port != 0 -> 1
                        it.ws_port != 0 -> 2
                        else -> 3
                    }
                }
            )
            val webSocketUrls = orderedHosts.mapNotNull { host ->
                val port = if (host.wss_port != 0) host.wss_port else host.ws_port
                if (host.host.isBlank() || port == 0) null
                else "${if (host.wss_port != 0) "wss" else "ws"}://${host.host}:$port/sub"
            }.distinct()

            com.android.purebilibili.core.util.Logger.d(
                "DanmakuRepo",
                "🔗 Connecting to live danmaku with ${webSocketUrls.size} server candidates"
            )

            if (webSocketUrls.isNotEmpty()) {
            val client = com.android.purebilibili.core.network.socket.LiveDanmakuClient(scope) // Removed onMessage and onPopularity as they are not defined in the original context
            
            // uid 与 token 必须同一账号；账号状态不完整时退回游客 uid=0，避免认证后强制断连
            val hasSess = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
            val uid = if (hasSess) (com.android.purebilibili.core.store.TokenManager.midCache ?: 0L) else 0L
            com.android.purebilibili.core.util.Logger.d("DanmakuRepo", "🔌 Connecting with UID: $uid")
            
            client.connect(webSocketUrls, token, realRoomId, uid)
            // liveDanmakuClient = client // liveDanmakuClient is not defined in the original context
            Result.success(client)
        } else {
            Result.failure(Exception("未找到有效的 WebSocket 地址"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("DanmakuRepo", "❌ Start live danmaku failed: ${e.message}", e)
        Result.failure(e)
        }
    }
}
