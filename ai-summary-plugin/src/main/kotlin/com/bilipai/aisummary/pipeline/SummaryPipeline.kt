package com.bilipai.aisummary.pipeline

import com.bilipai.aisummary.data.AudioTrackProvider
import com.bilipai.aisummary.data.BilibiliSubtitleApi
import com.bilipai.aisummary.data.ChatCompletionsClient
import com.bilipai.aisummary.data.GroqAsrClient
import com.bilipai.aisummary.data.OfficialConclusionApi
import com.bilipai.aisummary.data.SummaryCache
import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SegmentSummary
import com.bilipai.aisummary.model.SubtitleLine
import com.bilipai.aisummary.model.SummaryChapter
import com.bilipai.aisummary.model.SummaryDetailLevel
import com.bilipai.aisummary.model.SummaryException
import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.settings.ConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class SummaryRequest(
    val bvid: String,
    val cid: Long,
    val title: String,
    val upMid: Long = 0L
)

/** 宿主可把总结写进 BiliPai 的视频笔记草稿；不接也可以，传 null。 */
fun interface NoteDraftSink {
    fun save(result: SummaryResult, markdown: String)
}

typealias Progress = (stage: String, done: Int, total: Int) -> Unit

/**
 * 总结主流程（map-reduce）：
 *
 *  配置检查 → 读缓存 → 取字幕（失败则官方 AI 总结兜底）→ 分段
 *    → MAP：并发调用模型得到逐段要点（限流 + 单段失败只丢该段）
 *    → REDUCE：合并成总览/章节/术语/金句
 *    → 时间戳夹紧 → 写缓存 → 可选写入视频笔记草稿
 *
 * 所有网络与文件操作都跑在 Dispatchers.IO 上；进度通过 [Progress] 回调给 UI。
 */
class SummaryPipeline(
    private val configStore: ConfigStore,
    private val subtitleApi: BilibiliSubtitleApi,
    private val chat: ChatCompletionsClient,
    private val officialApi: OfficialConclusionApi? = null,
    private val cache: SummaryCache? = null,
    private val noteDraftSink: NoteDraftSink? = null,
    /** ASR 兜底：无字幕时转写音频（可为空表示不支持）。 */
    private val asrClient: GroqAsrClient? = null,
    private val audioTrackProvider: AudioTrackProvider? = null
) {
    suspend fun run(
        request: SummaryRequest,
        forceRefresh: Boolean = false,
        onProgress: Progress = { _, _, _ -> }
    ): SummaryResult = withContext(Dispatchers.IO) {
        val config = configStore.load()

        // 【优先官方】省额度模式：先试 B 站官方 conclusion/get（免费、秒出），
        // 官方没有或失败时才继续用自己的模型。默认关闭，因为官方覆盖率有限、内容较浅。
        if (config.preferOfficialSummary && officialApi != null) {
            onProgress("正在请求 B 站官方总结接口…", 0, 0)
            val official = runCatching {
                officialApi.fetch(
                    bvid = request.bvid, cid = request.cid, upMid = request.upMid,
                    title = request.title, style = config.style
                )
            }.getOrNull()
            if (official != null) {
                if (config.saveAsNoteDraft) noteDraftSink?.save(official, official.toMarkdown())
                onProgress("完成（官方总结）", 0, 0)
                return@withContext official
            }
            if (!config.isConfigured) {
                throw SummaryException(
                    "官方总结不可用，且还没有配置模型 API Key。请先在设置里填好 Key，" +
                        "或关闭「优先使用官方总结」。",
                    retryable = false
                )
            }
            onProgress("官方总结不可用，改用自定义模型…", 0, 0)
        }

        // 预检缓存：无字幕视频不需要先下载音频/跑 ASR，直接看有没有现成结果。
        // 旧逻辑把缓存键绑在字幕哈希上，必须 ASR 完才查得到——等于缓存对 ASR 视频无效。
        val cacheInstance = cache
        val baseKey = cacheInstance?.baseCacheKey(request.bvid, request.cid, config)
        if (!forceRefresh && config.cacheEnabled && baseKey != null) {
            cacheInstance.get(baseKey)?.let { cached ->
                onProgress("命中本地缓存", 1, 1)
                if (config.saveAsNoteDraft) noteDraftSink?.save(cached, cached.toMarkdown())
                return@withContext cached.copy(source = SummaryResult.Source.CACHE)
            }
        }

        onProgress("正在获取视频字幕（官方接口）…", 0, 0)
        val subtitle = try {
            subtitleApi.fetch(request.bvid, request.cid)
        } catch (e: SummaryException) {
            // ① 优先用 ASR 把音频转成字幕：这样仍然走「自己的模型总结」，
            //    而不是退化成官方那种只有 summary+outline 的浅结果。
            var asrFailure: String? = null
            if (config.asrConfigured && asrClient != null && audioTrackProvider != null) {
                // ASR 转写结果单独缓存：换模型/换档位不该重新下载音频、重复转写。
                val asrKey = cacheInstance?.asrCacheKey(request.bvid, request.cid, config)
                val cachedAsr = if (!forceRefresh && config.cacheEnabled && asrKey != null) {
                    cacheInstance.getAsr(asrKey)
                } else null
                if (cachedAsr != null) {
                    onProgress("命中 ASR 转写缓存（跳过音频下载）…", 0, 0)
                    return@withContext summarizeFromLines(
                        request = request,
                        config = config,
                        lines = cachedAsr,
                        source = SummaryResult.Source.LLM_ASR,
                        forceRefresh = forceRefresh,
                        onProgress = onProgress
                    )
                }
                val transcribed = runCatching {
                    onProgress("无字幕，正在用 ASR 转写音频…", 0, 0)
                    val track = audioTrackProvider.audioTrack(request.bvid, request.cid)
                        ?: throw SummaryException(
                            "拿不到该视频的音频流（可能需要登录，或视频不可见）",
                            retryable = false
                        )
                    asrClient.transcribe(config, track) { stage -> onProgress(stage, 0, 0) }
                }
                if (transcribed.isSuccess) {
                    val lines = transcribed.getOrThrow()
                    if (!forceRefresh && config.cacheEnabled && asrKey != null) {
                        cacheInstance.putAsr(asrKey, lines)
                    }
                    onProgress("ASR 完成，共 ${lines.size} 段，开始总结…", 0, 0)
                    return@withContext summarizeFromLines(
                        request = request,
                        config = config,
                        lines = lines,
                        source = SummaryResult.Source.LLM_ASR,
                        forceRefresh = forceRefresh,
                        onProgress = onProgress
                    )
                }
                asrFailure = transcribed.exceptionOrNull()?.message
                onProgress("ASR 失败（${asrFailure?.take(60)}），尝试官方兜底…", 0, 0)
            }
            if (config.fallbackToOfficial && officialApi != null) {
                onProgress("无字幕，改用 B 站官方 AI 总结…", 0, 0)
                val fallback = try {
                    officialApi.fetch(
                        bvid = request.bvid, cid = request.cid, upMid = request.upMid,
                        title = request.title, style = config.style
                    )
                } catch (officialError: SummaryException) {
                    // 兜底也失败时，必须把**原始字幕错误**一起带出来，
                    // 否则日志里只剩官方兜底的错误，无法判断到底是没字幕还是接口异常。
                    val asrPart = asrFailure?.let { "；ASR 兜底也失败：$it" }.orEmpty()
                    throw SummaryException(
                        "获取字幕失败：${e.message}$asrPart；官方总结兜底也失败：${officialError.message}",
                        retryable = e.retryable && officialError.retryable,
                        cause = e
                    )
                }
                if (config.saveAsNoteDraft) noteDraftSink?.save(fallback, fallback.toMarkdown())
                onProgress("完成", 0, 0)
                // 官方兜底结果不写缓存：无字幕视频没有可复用的字幕哈希，且官方数据由 B 站自己维护。
                return@withContext fallback
            }
            if (asrFailure != null) {
                throw SummaryException(
                    "获取字幕失败：${e.message}；ASR 兜底也失败：$asrFailure",
                    retryable = e.retryable,
                    cause = e
                )
            }
            throw e
        }

        if (!config.isConfigured) {
            throw SummaryException(
                "还没有配置模型 API Key，请先在设置里填好（或开启「无字幕时降级官方总结」）",
                retryable = false
            )
        }
        summarizeFromLines(
            request = request,
            config = config,
            lines = subtitle.lines,
            source = SummaryResult.Source.LLM_SUBTITLE,
            forceRefresh = forceRefresh,
            onProgress = onProgress
        )
    }

    /**
     * 用一段字幕（来自 B 站字幕或 ASR 转写）跑 map-reduce 生成总结。
     * 抽出来是为了让「有字幕」和「ASR 转写」两条路径共用同一套分段/缓存/合并逻辑。
     */
    private suspend fun summarizeFromLines(
        request: SummaryRequest,
        config: AiSummaryConfig,
        lines: List<SubtitleLine>,
        source: SummaryResult.Source,
        forceRefresh: Boolean,
        onProgress: Progress
    ): SummaryResult {
        // 显式局部变量：Kotlin 1.9 不会从 key != null 反推 cache 非空，
        // 而 2.4 会；用局部变量可让两个版本的流分析都成立且无多余安全调用。
        val cacheInstance = cache
        val key = cacheInstance?.cacheKey(request.bvid, request.cid, config, hashSubtitle(lines))
        if (!forceRefresh && config.cacheEnabled && key != null) {
            cacheInstance.get(key)?.let { cached ->
                onProgress("命中本地缓存", 1, 1)
                // 老版本只写了「视频+字幕哈希」键；给 ASR 结果补一份不含哈希的预检键，
                // 下次打开就能在取字幕/下载音频之前直接命中。
                if (cached.asrUsed) {
                    cacheInstance.put(cacheInstance.baseCacheKey(request.bvid, request.cid, config), cached)
                }
                if (config.saveAsNoteDraft) noteDraftSink?.save(cached, cached.toMarkdown())
                return cached.copy(source = SummaryResult.Source.CACHE)
            }
        }

        val chunks = SubtitleSegmenter(config.chunkCharBudget, config.overlapChars).segment(lines)
        if (chunks.isEmpty()) throw SummaryException("字幕为空，无法总结", retryable = false)

        onProgress("正在请求模型 ${config.model}（共 ${chunks.size} 段）…", 0, chunks.size)
        val segments = mapSegments(config, request.title, chunks, onProgress)
        if (segments.isEmpty()) {
            throw SummaryException("所有分段都失败了，请检查模型配置或稍后重试")
        }

        onProgress("正在请求模型 ${config.model}（合并总结）…", chunks.size, chunks.size)
        var reduceRaw = chat.chat(
            config = config,
            systemPrompt = PromptLibrary.systemReduce(config.style, config.detailLevel),
            userPrompt = PromptLibrary.userReduce(request.title, segments.map { it.rawText }),
            maxTokens = reduceMaxTokens(config),
            jsonMode = true
        )
        var draft = ResultParser.parseFinal(reduceRaw.content)
        if (!draft.parsedAsJson) {
            // 合并阶段的 JSON 被截断（常见于「详细」档 + 输出上限较低的网关）：
            // 先要求模型压缩成可解析的 JSON 重试一次，失败再用分段要点兜底。
            onProgress("模型输出被截断，正在用紧凑格式重试…", chunks.size, chunks.size)
            reduceRaw = chat.chat(
                config = config,
                systemPrompt = PromptLibrary.systemReduce(config.style, config.detailLevel) +
                    "\n\n" + PromptLibrary.COMPACT_RETRY_RULE,
                userPrompt = PromptLibrary.userReduce(request.title, segments.map { it.rawText }),
                maxTokens = reduceMaxTokens(config),
                jsonMode = true
            )
            draft = ResultParser.parseFinal(reduceRaw.content)
        }

        val duration = lines.lastOrNull()?.toSec ?: 0.0
        val chapters = PointTimer.assign(
            normalizeChapters(draft.chapters, segments, duration),
            lines,
            duration
        )
        val result = SummaryResult(
            bvid = request.bvid,
            cid = request.cid,
            title = request.title,
            style = config.style,
            providerId = config.providerId,
            model = config.model,
            createdAtMillis = System.currentTimeMillis(),
            overview = draft.overview.ifBlank { buildFallbackOverview(segments) },
            chapters = chapters,
            terms = draft.terms,
            quotes = draft.quotes,
            verdict = draft.verdict,
            source = source,
            asrUsed = source == SummaryResult.Source.LLM_ASR,
            promptTokens = reduceRaw.promptTokens,
            completionTokens = reduceRaw.completionTokens,
            segmentCount = chunks.size
        )

        if (config.cacheEnabled && cacheInstance != null) {
            if (key != null) cacheInstance.put(key, result)
            // 只有 ASR 结果才写「不含字幕哈希」的预检缓存：无字幕视频算不出字幕哈希，
            // 必须先跑完 ASR 才能查缓存，等于白存；有字幕的视频取字幕只是一次廉价请求，
            // 继续走精确键，UP 主改了字幕也能及时更新。
            if (result.asrUsed) {
                cacheInstance.put(cacheInstance.baseCacheKey(request.bvid, request.cid, config), result)
            }
        }
        if (config.saveAsNoteDraft) noteDraftSink?.save(result, result.toMarkdown())
        onProgress("完成", chunks.size, chunks.size)
        return result
    }

    /** 详略对输出长度的影响系数：精简 0.7×、标准 1×、详细 1.5×。 */
    private fun SummaryDetailLevel.tokenFactor(): Double = when (this) {
        SummaryDetailLevel.BRIEF -> 0.7
        SummaryDetailLevel.STANDARD -> 1.0
        SummaryDetailLevel.DETAILED -> 1.5
    }

    private fun mapMaxTokens(config: AiSummaryConfig): Int =
        (config.maxTokensPerSegment * config.detailLevel.tokenFactor())
            .toInt().coerceIn(400, 4096)

    private fun reduceMaxTokens(config: AiSummaryConfig): Int =
        (config.maxTokensPerSegment * 1.5 * config.detailLevel.tokenFactor())
            .toInt().coerceIn(600, 4096)

    private suspend fun mapSegments(
        config: AiSummaryConfig,
        title: String,
        chunks: List<com.bilipai.aisummary.model.SubtitleChunk>,
        onProgress: Progress
    ): List<MapSegment> = supervisorScope {
        val gate = Semaphore(config.maxConcurrency.coerceIn(1, 6))
        var done = 0
        val jobs = chunks.map { chunk ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    // 在子协程内部消化异常：普通 coroutineScope 下任一 async 抛异常会
                    // 立刻取消所有兄弟段，导致「单段失败只丢该段」完全失效。
                    runCatching {
                        var raw = chat.chat(
                            config = config,
                            systemPrompt = PromptLibrary.systemMap(config.style, config.detailLevel),
                            userPrompt = PromptLibrary.userMap(title, chunk, config.style),
                            maxTokens = mapMaxTokens(config),
                            jsonMode = true
                        )
                        var parsed = ResultParser.parseSegmentSummary(
                            raw.content, chunk.index, chunk.startSec, chunk.endSec
                        )
                        if (!parsed.parsedAsJson) {
                            // 输出被截断时用更紧凑的格式重试一次，避免半截 JSON 变成要点
                            raw = chat.chat(
                                config = config,
                                systemPrompt = PromptLibrary.systemMap(config.style, config.detailLevel) +
                                    "\n\n" + PromptLibrary.COMPACT_RETRY_RULE,
                                userPrompt = PromptLibrary.userMap(title, chunk, config.style),
                                maxTokens = mapMaxTokens(config),
                                jsonMode = true
                            )
                            parsed = ResultParser.parseSegmentSummary(
                                raw.content, chunk.index, chunk.startSec, chunk.endSec
                            )
                        }
                        MapSegment(parsed, raw.content)
                    }.getOrElse { e ->
                        if (e is CancellationException) throw e
                        null
                    }
                }
            }
        }
        val out = ArrayList<MapSegment>()
        jobs.forEach { job ->
            val r = try {
                job.await()
            } catch (e: CancellationException) {
                throw e // 协作式取消必须继续传播，不能被吞掉
            } catch (e: Exception) {
                null
            }
            if (r != null) out += r
            done++
            onProgress("正在请求模型 ${config.model}（逐段提炼 $done/${chunks.size}）…", done, chunks.size)
        }
        out.sortedBy { it.summary.index }
    }

    /** 章节时间戳夹紧 + grounded 校验：模型编造的时间点回退到它最可能所属的分段起点。 */
    private fun normalizeChapters(
        chapters: List<SummaryChapter>,
        segments: List<MapSegment>,
        duration: Double
    ): List<SummaryChapter> {
        val ranges = segments.map { it.summary.startSec..it.summary.endSec }
        val cleaned = chapters.map { c ->
            val t = c.startSec.coerceIn(0.0, duration)
            val grounded = ranges.any { t >= it.start - 1.0 && t <= it.endInclusive + 1.0 }
            val start = if (grounded) t else nearestSegmentStart(t, segments)
            c.copy(startSec = start, endSec = c.endSec.coerceIn(0.0, duration))
        }.sortedBy { it.startSec }
        if (cleaned.isNotEmpty()) return cleaned
        return segments.map { s ->
            SummaryChapter(
                title = "第 ${s.summary.index + 1} 段",
                startSec = s.summary.startSec,
                endSec = s.summary.endSec,
                points = s.summary.points.map { raw ->
                    ResultParser.parsePoint(raw)?.let { p ->
                        if (p.hasTime) p else p.copy(startSec = s.summary.startSec)
                    } ?: com.bilipai.aisummary.model.SummaryPoint(raw, s.summary.startSec)
                }
            )
        }
    }

    private fun nearestSegmentStart(t: Double, segments: List<MapSegment>): Double =
        segments.minByOrNull { kotlin.math.abs(it.summary.startSec - t) }
            ?.summary?.startSec ?: 0.0

    private fun buildFallbackOverview(segments: List<MapSegment>): String =
        segments.flatMap { it.summary.points }
            .map { ResultParser.parsePoint(it)?.text ?: it }
            .filter { it.isNotBlank() }
            .take(5).joinToString("；")

    private fun hashSubtitle(lines: List<com.bilipai.aisummary.model.SubtitleLine>): String =
        MessageDigest.getInstance("SHA-256")
            .digest(lines.joinToString("\n") { "${it.fromSec}:${it.content}" }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class MapSegment(val summary: SegmentSummary, val rawText: String)
}
