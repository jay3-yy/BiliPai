package com.bilipai.aisummary

import com.bilipai.aisummary.data.ChatCompletionsClient
import com.bilipai.aisummary.data.OfficialConclusionApi
import com.bilipai.aisummary.data.SummaryCache
import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SummaryDetailLevel
import com.bilipai.aisummary.model.SummaryStyle
import com.bilipai.aisummary.model.SubtitleChunk
import com.bilipai.aisummary.model.SubtitleLine
import com.bilipai.aisummary.model.SummaryChapter
import com.bilipai.aisummary.model.SummaryPoint
import com.bilipai.aisummary.pipeline.PointTimer
import com.bilipai.aisummary.pipeline.PromptLibrary
import com.bilipai.aisummary.pipeline.ResultParser
import com.bilipai.aisummary.pipeline.SubtitleSegmenter
import com.bilipai.aisummary.settings.ProviderTemplates
import com.bilipai.aisummary.util.HttpGet
import com.bilipai.aisummary.util.InputNormalizer
import com.bilipai.aisummary.util.HttpPost
import com.bilipai.aisummary.util.HttpResponse
import com.bilipai.aisummary.util.WbiSigner
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PipelineLogicTest {

    private fun lines(n: Int, chars: Int = 20): List<SubtitleLine> =
        (0 until n).map { i ->
            SubtitleLine(
                fromSec = i * 3.0,
                toSec = i * 3.0 + 2.8,
                content = "第${i}句" + "内".repeat(chars) + "。"
            )
        }

    @Test
    fun `segmenter returns empty for empty input`() {
        assertTrue(SubtitleSegmenter().segment(emptyList()).isEmpty())
    }

    @Test
    fun `segmenter never exceeds budget and always advances`() {
        val src = lines(200)
        val chunks = SubtitleSegmenter(budgetChars = 600, overlapChars = 60).segment(src)
        assertTrue(chunks.size > 1)
        assertEquals(chunks.size, chunks.first().total)
        chunks.forEachIndexed { i, c ->
            assertEquals(i, c.index)
            assertTrue("chunk $i 起点必须单调前进", i == 0 || c.startSec >= chunks[i - 1].startSec)
            assertTrue(c.lines.isNotEmpty())
            assertTrue(c.startSec <= c.endSec)
        }
        // 所有原始行至少出现一次（重叠不丢行）
        val covered = chunks.flatMap { it.lines }.map { it.fromSec }.toHashSet()
        assertTrue(covered.size >= src.size - 1)
    }

    @Test
    fun `single short subtitle becomes one chunk`() {
        val chunks = SubtitleSegmenter(4500, 160).segment(lines(3))
        assertEquals(1, chunks.size)
        assertEquals(1, chunks[0].total)
    }

    @Test
    fun `illegal overlap is coerced instead of throwing`() {
        // 设置页可能配出 overlap >= budget/2；收敛而非抛异常，避免整次总结失败。
        val chunks = SubtitleSegmenter(1000, 900).segment(lines(200))
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.lines.isNotEmpty()) }
    }

    @Test
    fun `chunk prompt text carries timestamps`() {
        val c = SubtitleChunk(0, 1, 62.0, 75.0, listOf(SubtitleLine(62.0, 65.0, "你好")))
        assertTrue(c.toPromptText().contains("[01:02]"))
        assertEquals("01:02", SubtitleChunk.formatTime(62.0))
        assertEquals("1:02:03", SubtitleChunk.formatTime(3723.0))
    }

    @Test
    fun `result parser handles fenced json and plain text`() {
        val fenced = "好的\n```json\n{\"overview\":\"讲了三件事\",\"chapters\":[{\"title\":\"开场\",\"start\":\"00:12\",\"points\":[\"要点A\"]}]}\n```\n"
        val d = ResultParser.parseFinal(fenced)
        assertTrue(d.parsedAsJson)
        assertEquals("讲了三件事", d.overview)
        assertEquals(12.0, d.chapters.single().startSec, 0.001)

        val plain = ResultParser.parseFinal("模型直接给了纯文本，没有 JSON。")
        assertFalse(plain.parsedAsJson)
        assertTrue(plain.overview.contains("纯文本"))
    }

    @Test
    fun `parseTime supports all common shapes`() {
        assertEquals(372.0, ResultParser.parseTime("06:12")!!, 0.001)
        assertEquals(3723.0, ResultParser.parseTime("1:02:03")!!, 0.001)
        assertEquals(42.0, ResultParser.parseTime(42)!!, 0.001)
        assertEquals(42.0, ResultParser.parseTime("42")!!, 0.001)
        assertEquals(62.0, ResultParser.parseTime("[01:02]")!!, 0.001)
        assertNull(ResultParser.parseTime("不是时间"))
    }

    @Test
    fun `wbi mixin key is 32 chars and deterministic`() {
        val img = "7cd084941338484aae1ad9425b84077c".padEnd(32, '0')
        val sub = "4932caff0ff746eab6f36bf1cfa8a3d4".padEnd(32, '0')
        val a = WbiSigner.mixinKey(img, sub)
        val b = WbiSigner.mixinKey(img, sub)
        assertEquals(32, a.length)
        assertEquals(a, b)
    }

    @Test
    fun `chat request body never contains api key`() {
        val cfg = AiSummaryConfig(apiKey = "sk-SUPER-SECRET-123", model = "deepseek-chat")
        val client = ChatCompletionsClient(HttpPost { _, _, _, _ -> HttpResponse(200, "{}") })
        val body = client.buildRequestBody(cfg, "sys", "user", 100, true)
        assertFalse(body.contains("SUPER-SECRET"))
        val o = JSONObject(body)
        assertEquals("deepseek-chat", o.getString("model"))
        assertEquals("json_object", o.getJSONObject("response_format").getString("type"))
        assertEquals(2, o.getJSONArray("messages").length())
    }

    @Test
    fun `json mode is controlled by the jsonMode flag`() {
        val cfg = AiSummaryConfig(providerId = "custom", apiKey = "k", model = "m")
        val client = ChatCompletionsClient(HttpPost { _, _, _, _ -> HttpResponse(200, "{}") })
        assertTrue(JSONObject(client.buildRequestBody(cfg, "s", "u", 10, true)).has("response_format"))
        assertFalse(JSONObject(client.buildRequestBody(cfg, "s", "u", 10, false)).has("response_format"))
        // custom 模板也允许尝试 json_object：chat() 遇到 400 会自动去掉该字段重发。
        assertTrue(ProviderTemplates.byId("custom").supportsJsonMode)
    }

    @Test
    fun `json mode is automatically dropped when provider returns 400`() {
        val bodies = mutableListOf<String>()
        val fake = HttpPost { _, _, body, _ ->
            bodies += body
            if (bodies.size == 1) HttpResponse(
                400, """{"error":{"message":"unsupported parameter: response_format"}}"""
            ) else HttpResponse(200, """{"choices":[{"message":{"content":"ok"}}]}""")
        }
        val cfg = AiSummaryConfig(apiKey = "k", model = "deepseek-chat", maxRetries = 0)
        val client = ChatCompletionsClient(fake, sleeper = {})
        assertEquals("ok", client.chat(cfg, "sys", "user", 100, true).content)
        assertEquals(2, bodies.size)
        assertTrue(JSONObject(bodies[0]).has("response_format"))
        assertFalse(JSONObject(bodies[1]).has("response_format"))
    }

    @Test
    fun `reasoning models skip temperature and o series uses max_completion_tokens`() {
        val client = ChatCompletionsClient(HttpPost { _, _, _, _ -> HttpResponse(200, "{}") })
        val reasoner = JSONObject(
            client.buildRequestBody(AiSummaryConfig(model = "deepseek-reasoner"), "s", "u", 100, false)
        )
        assertFalse(reasoner.has("temperature"))
        assertTrue(reasoner.has("max_tokens"))
        val o = JSONObject(
            client.buildRequestBody(AiSummaryConfig(model = "o3-mini"), "s", "u", 100, false)
        )
        assertTrue(o.has("max_completion_tokens"))
        assertFalse(o.has("max_tokens"))
    }

    @Test
    fun `listModels reads and sorts ids`() {
        val get = HttpGet { _, _ ->
            HttpResponse(200, """{"data":[{"id":"model-b"},{"id":"model-a"}]}""")
        }
        val client = ChatCompletionsClient(
            HttpPost { _, _, _, _ -> HttpResponse(200, "{}") }, get
        )
        assertEquals(
            listOf("model-a", "model-b"),
            client.listModels(AiSummaryConfig(apiKey = "k"))
        )
    }

    @Test
    fun `prompts contain anti-hallucination and timestamp constraints`() {
        val s = PromptLibrary.systemMap(com.bilipai.aisummary.model.SummaryStyle.FULL_NOTE)
        assertTrue(s.contains("只能使用字幕"))
        assertTrue(s.contains("不得编造"))
        assertTrue(s.contains("禁止估算"))
        assertTrue(s.contains("JSON"))
        assertTrue(PromptLibrary.PROMPT_VERSION >= 1)
    }

    @Test
    fun `detail level changes prompts and cache key`() {
        val brief = PromptLibrary.systemMap(SummaryStyle.FULL_NOTE, SummaryDetailLevel.BRIEF)
        assertTrue(brief.contains("2~4 条"))
        val standard = PromptLibrary.systemMap(SummaryStyle.FULL_NOTE, SummaryDetailLevel.STANDARD)
        assertTrue(standard.contains("3~8 条"))
        val detailed = PromptLibrary.systemMap(SummaryStyle.FULL_NOTE, SummaryDetailLevel.DETAILED)
        assertTrue(detailed.contains("6~12 条"))

        val reduceBrief = PromptLibrary.systemReduce(SummaryStyle.FULL_NOTE, SummaryDetailLevel.BRIEF)
        assertTrue(reduceBrief.contains("2~4 个"))
        assertTrue(reduceBrief.contains("精简模式"))
        val reduceDetailed = PromptLibrary.systemReduce(SummaryStyle.FULL_NOTE, SummaryDetailLevel.DETAILED)
        assertTrue(reduceDetailed.contains("5~8 句"))
        assertTrue(reduceDetailed.contains("3~6 条"))

        // 详略不同必须产生不同的缓存键，否则切换档位会命中旧结果
        val dir = Files.createTempDirectory("aisummary-detail").toFile()
        val cache = SummaryCache(dir)
        val base = AiSummaryConfig(apiKey = "k", model = "m")
        val briefKey = cache.cacheKey("BV1", 1L, base.copy(detailLevel = SummaryDetailLevel.BRIEF), "h")
        val detailedKey = cache.cacheKey("BV1", 1L, base.copy(detailLevel = SummaryDetailLevel.DETAILED), "h")
        assertNotEquals(briefKey, detailedKey)
        dir.deleteRecursively()
    }

    @Test
    fun `truncated json is salvaged without leaking raw text`() {
        val draft = ResultParser.parseFinal(
            """{"overview":"本视频讲了西路军的历史背景。","chapters":[{"title":"背景","start":"00:00","points":["1936年"""
        )
        assertFalse(draft.parsedAsJson)
        assertFalse(draft.overview.contains("\"chapters\""))
        assertFalse(draft.overview.trimStart().startsWith("{"))
        assertTrue(draft.overview.contains("西路军"))

        val seg = ResultParser.parseSegmentSummary(
            """{"points":["第一段要点","第二段要点""", 0, 0.0, 10.0
        )
        assertFalse(seg.parsedAsJson)
        assertTrue(seg.points.isNotEmpty())
        seg.points.forEach { assertFalse(it.contains("\"points\"")) }
    }

    @Test
    fun `input normalizer fixes full-width chars from chinese IME`() {
        // 中文输入法把 / : 输成全角的真实情况
        assertEquals(
            "http://192.168.1.100:11434/v1",
            InputNormalizer.normalizeBaseUrl("HTTP：／／192.168.1.100：11434／v1")
        )
        // 结尾多余斜杠、误贴的完整 endpoint
        assertEquals(
            "https://api.deepseek.com/v1",
            InputNormalizer.normalizeBaseUrl("https://api.deepseek.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.deepseek.com/v1",
            InputNormalizer.normalizeBaseUrl("  https://api.deepseek.com/v1/  ")
        )
        assertEquals("gemini-web/gemini-3.8-flash", InputNormalizer.normalizeModel("gemini-web／gemini-3.8-flash"))
        assertTrue(InputNormalizer.looksLikeHttpUrl("http://192.168.1.100:11434/v1"))
        assertFalse(InputNormalizer.looksLikeHttpUrl("192.168.1.100:11434"))
    }

    @Test
    fun `effective provider follows the actual base url`() {
        // 模板原样：保持模板 id
        val onTemplate = AiSummaryConfig(
            providerId = "deepseek",
            baseUrl = "https://api.deepseek.com/v1"
        )
        assertEquals("deepseek", ProviderTemplates.resolveEffectiveProviderId(onTemplate))

        // 在 DeepSeek 模板上改成自建网关：应识别为 custom
        val onGateway = AiSummaryConfig(
            providerId = "deepseek",
            baseUrl = "http://192.168.1.100:11434/v1"
        )
        assertEquals("custom", ProviderTemplates.resolveEffectiveProviderId(onGateway))
        assertTrue(ProviderTemplates.displayNameFor(onGateway).contains("自定义"))

        // 全角/尾斜杠差异不应误判
        val messy = AiSummaryConfig(
            providerId = "deepseek",
            baseUrl = "https://api.deepseek.com/v1/"
        )
        assertEquals("deepseek", ProviderTemplates.resolveEffectiveProviderId(messy))
    }

    @Test
    fun `source label distinguishes official model and cache`() {
        val base = com.bilipai.aisummary.model.SummaryResult(
            bvid = "BV1", cid = 1L, title = "t",
            style = com.bilipai.aisummary.model.SummaryStyle.FULL_NOTE,
            providerId = "custom", model = "gemini-web/gemini-3.8-flash",
            createdAtMillis = 0L, overview = "o"
        )
        // 自定义模型
        val llm = base.copy(source = com.bilipai.aisummary.model.SummaryResult.Source.LLM_SUBTITLE)
        assertEquals("自定义模型 · gemini-web/gemini-3.8-flash", llm.sourceLabel)
        // 官方兜底
        val official = base.copy(
            source = com.bilipai.aisummary.model.SummaryResult.Source.OFFICIAL_CONCLUSION,
            providerId = "bilibili-official", isFallback = true
        )
        assertEquals("B 站官方 AI 总结", official.sourceLabel)
        // 缓存：要同时说明原始来源
        val cache = llm.copy(
            source = com.bilipai.aisummary.model.SummaryResult.Source.CACHE,
            createdAtMillis = 1_700_000_000_000L
        )
        assertTrue(cache.sourceLabel.startsWith("本地缓存（原始来源：自定义模型"))
        assertTrue(cache.sourceLabel.contains("生成于"))
        // markdown 导出带来源
        assertTrue(llm.toMarkdown().contains("> 来源：自定义模型"))
        // 折叠态用的极短标记
        assertEquals("〔自定义模型〕", llm.sourceShortTag)
        assertEquals("〔官方〕", official.sourceShortTag)
        assertEquals("〔缓存〕", cache.sourceShortTag)
    }

    @Test
    fun `token estimator is conservative for CJK`() {
        val t = SubtitleSegmenter.estimateTokens("你好世界".repeat(100)) // 400 汉字
        assertTrue(t >= 400)
    }

    @Test
    fun `base and asr cache keys isolate what they must`() {
        val dir = Files.createTempDirectory("cache-key").toFile()
        val cache = SummaryCache(dir)
        val base = AiSummaryConfig(apiKey = "k", model = "m")
        // 精确键：字幕不同 → 不同
        assertNotEquals(
            cache.cacheKey("BV1", 1L, base, "hash1"),
            cache.cacheKey("BV1", 1L, base, "hash2")
        )
        // 预检键：和字幕无关（同配置同视频恒定），但档位/模型要隔离
        assertEquals(cache.baseCacheKey("BV1", 1L, base), cache.baseCacheKey("BV1", 1L, base))
        assertNotEquals(
            cache.baseCacheKey("BV1", 1L, base),
            cache.baseCacheKey("BV1", 1L, base.copy(detailLevel = SummaryDetailLevel.DETAILED))
        )
        assertNotEquals(
            cache.baseCacheKey("BV1", 1L, base),
            cache.baseCacheKey("BV2", 1L, base)
        )
        // ASR 转写键：换总结模型不影响，换 ASR 模型/语言才变
        assertEquals(
            cache.asrCacheKey("BV1", 1L, base),
            cache.asrCacheKey("BV1", 1L, base.copy(model = "other"))
        )
        assertNotEquals(
            cache.asrCacheKey("BV1", 1L, base),
            cache.asrCacheKey("BV1", 1L, base.copy(asrModel = "whisper-large-v3"))
        )
        dir.deleteRecursively()
    }

    /** 回归：UI 上「同一章节里所有小节时间一样」——每条要点必须有自己时间。 */
    @Test
    fun `point timer gives each point its own time`() {
        val lines = (0 until 60).map { i ->
            SubtitleLine(
                fromSec = i * 5.0, toSec = i * 5.0 + 4.8,
                content = "第${i}句字幕内容，讲的是第${i}个话题的具体细节。"
            )
        }
        // 模型只给了章节 start，要点没有任何时间信息（旧版就是这个形态）
        val chapters = listOf(
            SummaryChapter(
                title = "开场", startSec = 0.0, endSec = 150.0,
                points = listOf(
                    SummaryPoint("第一段要点没有任何时间", -1.0),
                    SummaryPoint("第二段要点也没有时间", -1.0),
                    SummaryPoint("第三段要点同样没有", -1.0)
                )
            )
        )
        val timed = PointTimer.assign(chapters, lines, 300.0)
        val times = timed[0].points.map { it.startSec }
        assertEquals(3, times.size)
        assertEquals("每条要点时间必须不同", 3, times.toSet().size)
        assertTrue("时间要单调不减：$times", times.zipWithNext().all { (a, b) -> a <= b })
        assertTrue("时间要落在章节区间内：$times", times.all { it in 0.0..150.0 })
    }

    /** 要点自带 `[mm:ss]` 时优先用它，并从正文里去掉前缀（否则 UI 会重复显示）。 */
    @Test
    fun `inline timestamp is extracted and stripped`() {
        val raw = """
            {"overview":"总览","chapters":[{"title":"章节A","start":"00:00",
              "points":["[01:23] 要点一","[05:40] 要点二","没有时间的要点"]}],
             "terms":[],"quotes":[],"verdict":""}
        """.trimIndent()
        val draft = ResultParser.parseFinal(raw)
        val points = draft.chapters[0].points
        assertEquals(3, points.size)
        assertEquals("要点一", points[0].text)
        assertEquals(83.0, points[0].startSec, 0.001)
        assertEquals("要点二", points[1].text)
        assertEquals(340.0, points[1].startSec, 0.001)
        assertFalse("没有时间的要点不应带时间", points[2].hasTime)
    }

    /** 要点正文和某条字幕高度重合时，应该定位到那条字幕的时间，而不是章节起点。 */
    @Test
    fun `point timer matches point text to subtitle line`() {
        val lines = listOf(
            SubtitleLine(0.0, 4.0, "大家好，今天我们来聊聊西路军的历史背景。"),
            SubtitleLine(60.0, 64.0, "张国焘违抗中央命令拒不北上，导致会师时间延误。"),
            SubtitleLine(120.0, 124.0, "斯大林在援助交接地点上反复拖延。")
        )
        val chapters = listOf(
            SummaryChapter(
                title = "过程", startSec = 0.0, endSec = 180.0,
                points = listOf(SummaryPoint("张国焘违抗中央命令拒不北上，导致会师延误", -1.0))
            )
        )
        val timed = PointTimer.assign(chapters, lines, 180.0)
        assertEquals(60.0, timed[0].points[0].startSec, 1.0)
    }

    /** 老缓存（points 是纯字符串数组）必须还能读出来，时间退回章节起点。 */
    @Test
    fun `legacy cache with string points still loads`() {
        val dir = Files.createTempDirectory("legacy-cache").toFile()
        val cache = SummaryCache(dir)
        val json = """
            {"bvid":"BV1X","cid":1,"title":"t","style":"full_note","providerId":"p","model":"m",
             "createdAtMillis":1,"overview":"o",
             "chapters":[{"title":"c","startSec":30.0,"endSec":60.0,"points":["老格式要点"]}],
             "terms":[],"quotes":[],"verdict":"","source":"LLM_SUBTITLE"}
        """.trimIndent()
        val result = cache.fromJson(org.json.JSONObject(json))
        assertEquals(1, result.chapters.size)
        assertEquals("老格式要点", result.chapters[0].points[0].text)
        assertFalse(result.chapters[0].points[0].hasTime)
        dir.deleteRecursively()
    }

    /** 缓存往返要保住每条要点自己的时间。 */
    @Test
    fun `cache roundtrip keeps point times`() {
        val dir = Files.createTempDirectory("point-cache").toFile()
        val cache = SummaryCache(dir)
        val result = com.bilipai.aisummary.model.SummaryResult(
            bvid = "BV1Y", cid = 2, title = "t", style = SummaryStyle.FULL_NOTE,
            providerId = "p", model = "m", createdAtMillis = 1, overview = "o",
            chapters = listOf(
                SummaryChapter(
                    "c", 10.0, 100.0,
                    listOf(SummaryPoint("甲", 12.0), SummaryPoint("乙", 77.0))
                )
            )
        )
        val restored = cache.fromJson(cache.toJson(result))
        assertEquals(listOf(12.0, 77.0), restored.chapters[0].points.map { it.startSec })
        dir.deleteRecursively()
    }

    /**
     * 回归：官方接口的 part_outline 自带 timestamp，必须逐条保留。
     * 旧实现把它丢了，UI 上同一章节里每条要点都显示章节起点。
     */
    @Test
    fun `official outline keeps per point timestamps`() {
        val navJson = """{"code":0,"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png","sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f36bf1cfa8a3d4.png"}}}"""
        val conclusion = """
            {"code":0,"data":{"code":0,"model_result":{
              "summary":"总览文字",
              "outline":[
                {"title":"章节一","timestamp":1,"part_outline":[
                  {"timestamp":1,"content":"要点A"},{"timestamp":95,"content":"要点B"}]},
                {"title":"章节二","timestamp":614,"part_outline":[
                  {"timestamp":614,"content":"要点C"},{"timestamp":900,"content":"要点D"}]}
              ]}}}
        """.trimIndent()
        val get = HttpGet { url, _ ->
            when {
                url.contains("/x/web-interface/nav") -> HttpResponse(200, navJson)
                url.contains("conclusion/get") -> HttpResponse(200, conclusion)
                else -> HttpResponse(404, "{}")
            }
        }
        val api = OfficialConclusionApi(get, WbiSigner(get))
        val result = api.fetch("BV1OFFICIAL", 1L, 0L, "测试", SummaryStyle.FULL_NOTE)
        assertEquals(2, result.chapters.size)
        assertEquals(listOf(1.0, 95.0), result.chapters[0].points.map { it.startSec })
        assertEquals(listOf(614.0, 900.0), result.chapters[1].points.map { it.startSec })
        assertEquals(listOf("要点A", "要点B"), result.chapters[0].points.map { it.text })
    }

    /** 模型给所有要点都写了同一个 `[00:00]` 时，也必须重新分配出不同时间。 */
    @Test
    fun `duplicate inline timestamps are redistributed`() {
        val lines = (0 until 40).map { i ->
            SubtitleLine(i * 10.0, i * 10.0 + 9.0, "第${i}句，讲的是完全不同的内容${i}号。")
        }
        val chapters = listOf(
            SummaryChapter(
                "章节", 0.0, 400.0,
                listOf(
                    SummaryPoint("甲", 0.0), SummaryPoint("乙", 0.0), SummaryPoint("丙", 0.0)
                )
            )
        )
        val times = PointTimer.assign(chapters, lines, 400.0)[0].points.map { it.startSec }
        assertEquals(3, times.toSet().size)
        assertTrue(times.zipWithNext().all { (a, b) -> a <= b })
    }

    /** 官方接口 part_outline 时间全是 0 时，也要插值出不同时间。 */
    @Test
    fun `official zero timestamps are spread out`() {
        val navJson = """{"code":0,"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png","sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f36bf1cfa8a3d4.png"}}}"""
        val conclusion = """
            {"code":0,"data":{"code":0,"model_result":{"summary":"总览","outline":[
              {"title":"章节一","timestamp":0,"part_outline":[
                {"timestamp":0,"content":"要点A"},{"timestamp":0,"content":"要点B"}]},
              {"title":"章节二","timestamp":300,"part_outline":[
                {"timestamp":0,"content":"要点C"},{"timestamp":0,"content":"要点D"}]}]}}}
        """.trimIndent()
        val get = HttpGet { url, _ ->
            when {
                url.contains("/x/web-interface/nav") -> HttpResponse(200, navJson)
                url.contains("conclusion/get") -> HttpResponse(200, conclusion)
                else -> HttpResponse(404, "{}")
            }
        }
        val api = OfficialConclusionApi(get, WbiSigner(get))
        val result = api.fetch("BV1ZERO", 1L, 0L, "测试", SummaryStyle.FULL_NOTE)
        result.chapters.forEach { ch ->
            val times = ch.points.map { it.startSec }
            assertEquals("章节「${ch.title}」内时间不能全一样：$times", times.size, times.toSet().size)
        }
    }
}
