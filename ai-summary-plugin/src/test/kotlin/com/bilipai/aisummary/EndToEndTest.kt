package com.bilipai.aisummary

import com.bilipai.aisummary.data.BilibiliSubtitleApi
import com.bilipai.aisummary.data.AudioTrack
import com.bilipai.aisummary.data.AudioTrackProvider
import com.bilipai.aisummary.data.ChatCompletionsClient
import com.bilipai.aisummary.data.GroqAsrClient
import com.bilipai.aisummary.data.SummaryCache
import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.pipeline.SummaryPipeline
import com.bilipai.aisummary.pipeline.SummaryRequest
import com.bilipai.aisummary.settings.InMemoryConfigStore
import com.bilipai.aisummary.util.HttpDownload
import com.bilipai.aisummary.util.HttpGet
import com.bilipai.aisummary.util.HttpUpload
import com.bilipai.aisummary.util.HttpPost
import com.bilipai.aisummary.util.HttpResponse
import com.bilipai.aisummary.util.WbiSigner
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** 用假 HTTP 跑通完整流水线：字幕 → 分段 → MAP → REDUCE → 缓存 → 二次命中。 */
class EndToEndTest {

    private val fakeGet = HttpGet { url, _ ->
        when {
            url.contains("/x/web-interface/nav") -> HttpResponse(
                200,
                """{"code":0,"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png","sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f36bf1cfa8a3d4.png"}}}"""
            )
            url.contains("/x/player/") -> HttpResponse(
                200,
                """{"code":0,"data":{"subtitle":{"subtitles":[{"lan":"zh-CN","subtitle_url":"//i0.hdslb.com/sub.json"}]}}}"""
            )
            url.contains("sub.json") -> HttpResponse(200, subtitleJson())
            else -> HttpResponse(404, "{}")
        }
    }

    private var chatCalls = 0
    private val fakePost = HttpPost { _, _, body, _ ->
        chatCalls++
        val user = JSONObject(body).getJSONArray("messages").getJSONObject(1).getString("content")
        val reply = if (user.contains("当前片段")) {
            """{"points":["要点：字幕里讲了一个概念 [00:06]"],"timestamps":["00:06"]}"""
        } else {
            """{"overview":"这是一个测试视频，主要讲了三件事。","chapters":[{"title":"开场","start":"00:06","points":["要点A"]}],"terms":["术语A"],"quotes":[],"verdict":"适合快速浏览"}"""
        }
        HttpResponse(
            200,
            JSONObject().put("choices", org.json.JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", reply))
            )).put("usage", JSONObject().put("prompt_tokens", 100).put("completion_tokens", 50))
                .toString()
        )
    }

    private fun subtitleJson(): String {
        val arr = org.json.JSONArray()
        for (i in 0 until 120) {
            arr.put(JSONObject().put("from", i * 3.0).put("to", i * 3.0 + 2.8)
                .put("content", "第${i}句：这里讲了一个重要的知识点，值得记下来。"))
        }
        return JSONObject().put("body", arr).toString()
    }

    private fun pipeline(store: InMemoryConfigStore, cache: SummaryCache): SummaryPipeline {
        val signer = WbiSigner(fakeGet)
        return SummaryPipeline(
            configStore = store,
            subtitleApi = BilibiliSubtitleApi(fakeGet, signer),
            chat = ChatCompletionsClient(fakePost),
            cache = cache
        )
    }

    @Test
    fun `full pipeline produces structured result and cache works`(): Unit = runBlocking {
        val dir = Files.createTempDirectory("aisummary-test").toFile()
        val cache = SummaryCache(dir)
        val store = InMemoryConfigStore(
            AiSummaryConfig(
                apiKey = "sk-test", model = "deepseek-chat",
                chunkCharBudget = 800, overlapChars = 80, maxConcurrency = 2
            )
        )
        val request = SummaryRequest("BV1TEST00001", 123456L, "测试视频")
        val progress = mutableListOf<String>()

        val first = pipeline(store, cache).run(request) { stage, _, _ -> progress += stage }
        assertTrue(first.overview.contains("测试视频"))
        assertEquals(1, first.chapters.size)
        assertEquals(6.0, first.chapters[0].startSec, 0.001)
        assertTrue(first.segmentCount > 1)
        assertEquals(SummaryResult.Source.LLM_SUBTITLE, first.source)
        assertTrue(progress.any { it.contains("逐段") })
        val callsAfterFirst = chatCalls

        val second = pipeline(store, cache).run(request)
        assertEquals(SummaryResult.Source.CACHE, second.source)
        assertEquals(callsAfterFirst, chatCalls) // 命中缓存，没有再调用模型
        assertEquals(first.overview, second.overview)

        dir.deleteRecursively()
    }

    /** 回归：loading 卡片展示的是真实阶段（字幕 / 模型请求），不是固定文案。 */
    /** 合并阶段输出被截断时，自动用紧凑格式重试，且绝不把半截 JSON 当正文。 */
    @Test
    fun `truncated reduce json is retried compactly and never shown raw`(): Unit = runBlocking {
        val dir = Files.createTempDirectory("aisummary-trunc").toFile()
        val cache = SummaryCache(dir)
        var reduceCalls = 0
        val post = HttpPost { _, _, body, _ ->
            val messages = JSONObject(body).getJSONArray("messages")
            val system = messages.getJSONObject(0).getString("content")
            val user = messages.getJSONObject(1).getString("content")
            val reply = when {
                user.contains("当前片段") -> """{"points":["要点A [00:06]"],"timestamps":["00:06"]}"""
                system.contains("上一次输出因为长度限制被截断") ->
                    """{"overview":"重试后的完整总结。","chapters":[{"title":"开场","start":"00:06","points":["要点A"]}],"terms":[],"quotes":[],"verdict":"值得一看"}"""
                else -> {
                    reduceCalls++
                    """{"overview":"这是一个被截断的总结","chapters":[{"title":"开场","start":"00:06","points":["要点A"""
                }
            }
            HttpResponse(
                200,
                JSONObject().put("choices", org.json.JSONArray().put(
                    JSONObject().put("message", JSONObject().put("content", reply))
                )).toString()
            )
        }
        val store = InMemoryConfigStore(
            AiSummaryConfig(
                apiKey = "sk-test", model = "m",
                chunkCharBudget = 800, overlapChars = 80, maxConcurrency = 1,
                detailLevel = com.bilipai.aisummary.model.SummaryDetailLevel.DETAILED
            )
        )
        val progress = mutableListOf<String>()
        val pipe = SummaryPipeline(
            configStore = store,
            subtitleApi = BilibiliSubtitleApi(fakeGet, WbiSigner(fakeGet)),
            chat = ChatCompletionsClient(post),
            cache = cache
        )
        val result = pipe.run(SummaryRequest("BV1TRUNC", 1L, "截断测试")) { stage, _, _ ->
            progress += stage
        }
        assertEquals(1, reduceCalls)
        assertTrue(progress.any { it.contains("被截断") })
        assertTrue(result.overview.contains("重试后的完整总结"))
        assertFalse(result.overview.contains("\"chapters\""))
        assertTrue(result.chapters.isNotEmpty())
        assertEquals(SummaryResult.Source.LLM_SUBTITLE, result.source)
        dir.deleteRecursively()
    }

    @Test
    fun `progress reports real stages for subtitle and model requests`(): Unit = runBlocking {
        val dir = Files.createTempDirectory("aisummary-progress").toFile()
        val cache = SummaryCache(dir)
        val store = InMemoryConfigStore(
            AiSummaryConfig(
                apiKey = "sk-test", model = "deepseek-chat",
                chunkCharBudget = 800, overlapChars = 80, maxConcurrency = 2
            )
        )
        val progress = mutableListOf<String>()
        pipeline(store, cache).run(SummaryRequest("BV1PROGRESS", 42L, "进度视频")) { stage, _, _ ->
            progress += stage
        }
        assertTrue(progress.any { it.startsWith("正在获取视频字幕") })
        assertTrue(progress.any { it.contains("正在请求模型") && it.contains("逐段提炼") })
        assertTrue(progress.any { it.contains("正在请求模型") && it.contains("合并总结") })
        dir.deleteRecursively()
    }

    @Test
    fun `missing config asks user to configure instead of calling network`(): Unit = runBlocking {
        val store = InMemoryConfigStore(AiSummaryConfig(apiKey = ""))
        val e = runCatching { pipeline(store, SummaryCache(Files.createTempDirectory("aisummary-test2").toFile())).run(SummaryRequest("BV1", 1L, "x")) }
        assertTrue(e.isFailure)
        assertTrue(e.exceptionOrNull()!!.message!!.contains("配置"))
    }

    /** 回归：普通 coroutineScope 下单个 async 失败会取消全部兄弟段，改用 supervisorScope 后不应如此。 */
    @Test
    fun `a failing segment is dropped without cancelling the rest`(): Unit = runBlocking {
        val post = HttpPost { _, _, body, _ ->
            val user = JSONObject(body).getJSONArray("messages").getJSONObject(1).getString("content")
            if (user.contains("当前片段") && user.contains("第 2/")) {
                throw java.io.IOException("simulated segment timeout")
            }
            val reply = if (user.contains("当前片段")) {
                """{"points":["要点 [00:06]"],"timestamps":["00:06"]}"""
            } else {
                """{"overview":"合并后的总览","chapters":[{"title":"开场","start":"00:06","points":["要点A"]}]}"""
            }
            HttpResponse(
                200,
                JSONObject().put("choices", org.json.JSONArray().put(
                    JSONObject().put("message", JSONObject().put("content", reply))
                )).put("usage", JSONObject().put("prompt_tokens", 10).put("completion_tokens", 5))
                    .toString()
            )
        }
        val store = InMemoryConfigStore(
            AiSummaryConfig(
                apiKey = "k", model = "m", chunkCharBudget = 800, overlapChars = 80,
                maxRetries = 0, cacheEnabled = false
            )
        )
        val signer = WbiSigner(fakeGet)
        val p = SummaryPipeline(
            configStore = store,
            subtitleApi = BilibiliSubtitleApi(fakeGet, signer),
            chat = ChatCompletionsClient(post, sleeper = {})
        )
        val result = p.run(SummaryRequest("BV1FLAKY", 1L, "不稳定视频"))
        assertTrue(result.overview.isNotBlank())
        assertTrue(result.segmentCount > 1)
    }

    /**
     * 回归：无字幕视频第二次打开必须先命中缓存，不能重新下载音频。
     * 旧实现把缓存键绑在字幕哈希上，必须先下载音频、跑完 ASR 才能查缓存。
     */
    @Test
    fun `preflight cache skips asr download on second open`(): Unit = runBlocking {
        val dir = Files.createTempDirectory("aisummary-preflight").toFile()
        val cache = SummaryCache(dir)
        val navJson = """{"code":0,"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png","sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f36bf1cfa8a3d4.png"}}}"""
        val get = HttpGet { url, _ ->
            when {
                url.contains("/x/web-interface/nav") -> HttpResponse(200, navJson)
                url.contains("/x/player/") -> HttpResponse(
                    200, """{"code":0,"data":{"subtitle":{"subtitles":[]}}}"""
                )
                else -> HttpResponse(404, "{}")
            }
        }
        var downloads = 0
        var trackCalls = 0
        val download = HttpDownload { _, _, dest, _, _, _ ->
            downloads++
            dest.writeBytes(ByteArray(1024))
            1024L
        }
        val upload = HttpUpload { _, _, _, _, _, _, _, _, _ ->
            HttpResponse(
                200,
                """{"segments":[{"start":0.0,"end":3.0,"text":"第一句"},{"start":3.0,"end":6.0,"text":"第二句"}]}"""
            )
        }
        val asr = GroqAsrClient(download, upload, Files.createTempDirectory("asr-tmp").toFile())
        val provider = AudioTrackProvider { _, _ ->
            trackCalls++
            AudioTrack(url = "https://example.com/a.m4s", bandwidth = 128000, durationSec = 60.0, mimeType = "audio/mp4")
        }
        val store = InMemoryConfigStore(
            AiSummaryConfig(
                apiKey = "sk-test", model = "deepseek-chat", chunkCharBudget = 800,
                asrEnabled = true, asrApiKey = "gsk-test",
                asrBaseUrl = "https://api.groq.com/openai/v1",
                asrModel = "whisper-large-v3-turbo", asrLanguage = "zh"
            )
        )
        fun build() = SummaryPipeline(
            configStore = store,
            subtitleApi = BilibiliSubtitleApi(get, WbiSigner(get)),
            chat = ChatCompletionsClient(fakePost),
            cache = cache,
            asrClient = asr,
            audioTrackProvider = provider
        )
        val request = SummaryRequest("BV1PREFLIGHT", 111L, "无字幕视频")
        val first = build().run(request)
        assertEquals(SummaryResult.Source.LLM_ASR, first.source)
        assertEquals(1, downloads)

        val progress = mutableListOf<String>()
        val second = build().run(request) { stage, _, _ -> progress += stage }
        assertEquals(SummaryResult.Source.CACHE, second.source)
        assertEquals(first.overview, second.overview)
        assertEquals(1, downloads)   // 没有再下载音频
        assertEquals(1, trackCalls)  // 连音轨都没去取
        assertTrue(progress.any { it.contains("命中本地缓存") })
        dir.deleteRecursively()
    }

    /**
     * 回归：换了模型导致预检缓存失效时，无字幕视频应复用 ASR 转写文本，
     * 不再重复下载音频、重复调用 Groq。
     */
    @Test
    fun `asr transcript cache avoids audio download on rerun`(): Unit = runBlocking {
        val dir = Files.createTempDirectory("aisummary-asr").toFile()
        val cache = SummaryCache(dir)
        val navJson = """{"code":0,"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png","sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f36bf1cfa8a3d4.png"}}}"""
        val get = HttpGet { url, _ ->
            when {
                url.contains("/x/web-interface/nav") -> HttpResponse(200, navJson)
                url.contains("/x/player/") -> HttpResponse(
                    200, """{"code":0,"data":{"subtitle":{"subtitles":[]}}}"""
                )
                else -> HttpResponse(404, "{}")
            }
        }
        var downloads = 0
        val download = HttpDownload { _, _, dest, _, _, _ ->
            downloads++
            dest.writeBytes(ByteArray(1024))
            1024L
        }
        val upload = HttpUpload { _, _, _, _, _, _, _, _, _ ->
            HttpResponse(
                200,
                """{"segments":[{"start":0.0,"end":3.0,"text":"第一句"},{"start":3.0,"end":6.0,"text":"第二句"}]}"""
            )
        }
        val asr = GroqAsrClient(download, upload, Files.createTempDirectory("asr-tmp").toFile())
        var trackCalls = 0
        val provider = AudioTrackProvider { _, _ ->
            trackCalls++
            AudioTrack(url = "https://example.com/a.m4s", bandwidth = 128000, durationSec = 60.0, mimeType = "audio/mp4")
        }
        val baseConfig = AiSummaryConfig(
            apiKey = "sk-test", model = "deepseek-chat", chunkCharBudget = 800,
            asrEnabled = true, asrApiKey = "gsk-test",
            asrBaseUrl = "https://api.groq.com/openai/v1",
            asrModel = "whisper-large-v3-turbo", asrLanguage = "zh"
        )
        val store = InMemoryConfigStore(baseConfig)
        fun build() = SummaryPipeline(
            configStore = store,
            subtitleApi = BilibiliSubtitleApi(get, WbiSigner(get)),
            chat = ChatCompletionsClient(fakePost),
            cache = cache,
            asrClient = asr,
            audioTrackProvider = provider
        )
        val request = SummaryRequest("BV1ASR00001", 222L, "无字幕视频")
        val first = build().run(request)
        assertEquals(SummaryResult.Source.LLM_ASR, first.source)
        assertEquals(1, downloads)
        assertEquals(1, trackCalls)

        // 换模型 → base key 变了，但同一份 ASR 转写应直接复用
        store.save(baseConfig.copy(model = "deepseek-chat-2"))
        val progress = mutableListOf<String>()
        val second = build().run(request) { stage, _, _ -> progress += stage }
        assertEquals(1, downloads)
        assertEquals(1, trackCalls)
        assertEquals(SummaryResult.Source.LLM_ASR, second.source)
        assertTrue(progress.any { it.contains("命中 ASR 转写缓存") })
        dir.deleteRecursively()
    }

    /** 回归：模型给每条要点带 `[mm:ss]` 时，结果里每条要点必须有**不同**的时间。 */
    @Test
    fun `pipeline keeps distinct per point timestamps`(): Unit = runBlocking {
        val dir = Files.createTempDirectory("aisummary-pointtime").toFile()
        val cache = SummaryCache(dir)
        val post = HttpPost { _, _, body, _ ->
            val messages = JSONObject(body).getJSONArray("messages")
            val user = messages.getJSONObject(1).getString("content")
            val reply = if (user.contains("当前片段")) {
                """{"points":["[00:06] 要点A","[00:12] 要点B"],"timestamps":["00:06","00:12"]}"""
            } else {
                """{"overview":"总览","chapters":[{"title":"第一章","start":"00:06",
                   "points":["[00:06] 第一条要点","[02:30] 第二条要点","[05:10] 第三条要点"]}],
                   "terms":[],"quotes":[],"verdict":""}"""
            }
            HttpResponse(
                200,
                JSONObject().put("choices", org.json.JSONArray().put(
                    JSONObject().put("message", JSONObject().put("content", reply))
                )).toString()
            )
        }
        val store = InMemoryConfigStore(
            AiSummaryConfig(apiKey = "sk-test", model = "deepseek-chat", chunkCharBudget = 800)
        )
        val pipeline = SummaryPipeline(
            configStore = store,
            subtitleApi = BilibiliSubtitleApi(fakeGet, WbiSigner(fakeGet)),
            chat = ChatCompletionsClient(post),
            cache = cache
        )
        val result = pipeline.run(SummaryRequest("BV1POINTTIME", 999L, "测试视频"))
        val points = result.chapters.first().points
        assertEquals(3, points.size)
        assertEquals(listOf(6.0, 150.0, 310.0), points.map { it.startSec })
        // 前缀必须从正文里去掉，否则 UI 上会出现「时间 + 时间」重复
        assertEquals(listOf("第一条要点", "第二条要点", "第三条要点"), points.map { it.text })
        dir.deleteRecursively()
    }
}
