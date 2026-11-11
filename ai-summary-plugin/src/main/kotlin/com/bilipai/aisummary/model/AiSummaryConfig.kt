package com.bilipai.aisummary.model

/** 三种内置总结风格，对应 [com.bilipai.aisummary.pipeline.PromptLibrary] 里的三套 Prompt。 */
enum class SummaryStyle(val id: String, val displayName: String) {
    QUICK("quick", "快速总结"),
    FULL_NOTE("full_note", "完整笔记"),
    OUTLINE("outline", "章节大纲");

    companion object {
        fun fromId(id: String?): SummaryStyle = entries.firstOrNull { it.id == id } ?: FULL_NOTE
    }
}

/**
 * 总结的详略程度（与「风格」正交）：
 * - 风格决定**结构**（快速总结 / 完整笔记 / 章节大纲）；
 * - 详略决定**信息密度与篇幅**（要点条数、章节数、overview 长度、token 预算）。
 * 两者冲突时以详略为准（Prompt 里已写明）。
 */
enum class SummaryDetailLevel(
    val id: String,
    val displayName: String,
    val description: String
) {
    BRIEF("brief", "精简", "只保留结论与关键点，适合快速浏览"),
    STANDARD("standard", "标准", "结论 + 章节要点，信息密度均衡（默认）"),
    DETAILED("detailed", "详细", "保留论证、数据与例子，篇幅更长");

    companion object {
        fun fromId(id: String?): SummaryDetailLevel =
            entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** 默认值的单一来源：与 settings/ProviderTemplates 的第一个模板保持一致。 */
object AiSummaryDefaults {
    const val PROVIDER_ID = "deepseek"
    const val BASE_URL = "https://api.deepseek.com/v1"
    const val MODEL = "deepseek-chat"
}

/**
 * 插件全部配置。apiKey 只存在于内存与加密存储中，
 * 严禁写入日志、崩溃上报、备份文件或任何 toString()。
 */
data class AiSummaryConfig(
    val providerId: String = AiSummaryDefaults.PROVIDER_ID,
    val baseUrl: String = AiSummaryDefaults.BASE_URL,
    val apiKey: String = "",
    val model: String = AiSummaryDefaults.MODEL,
    val temperature: Float = 0.3f,
    /** 每段最多允许模型输出的 token 数。 */
    val maxTokensPerSegment: Int = 1600,
    /** 单个分段输入字幕的字符预算（中文按 1 字 ≈ 1.5 token 保守估算）。 */
    val chunkCharBudget: Int = 4500,
    /** 相邻分段之间的重叠字符数，避免切断一句话导致要点丢失。 */
    val overlapChars: Int = 160,
    val maxConcurrency: Int = 2,
    val timeoutSeconds: Int = 90,
    val maxRetries: Int = 2,
    val style: SummaryStyle = SummaryStyle.FULL_NOTE,
    /** 输出详略程度：影响 Prompt 与 token 预算；与风格冲突时以详略为准。 */
    val detailLevel: SummaryDetailLevel = SummaryDetailLevel.STANDARD,
    /** 拿不到字幕时，是否允许降级为 B 站官方 AI 总结（不消耗用户模型额度）。 */
    /**
     * 是否**优先**使用 B 站官方 AI 总结（省模型额度）。
     *
     * false（默认）：先用字幕 + 自己的模型生成；官方总结只在「无字幕」或「插件调用失败」时兜底。
     * true：先请求官方 `conclusion/get`（免费、秒出），官方没有或失败时才用自己的模型。
     * 官方覆盖率有限且内容较浅（只有 summary + outline），所以默认不优先。
     */
    val preferOfficialSummary: Boolean = false,
    val fallbackToOfficial: Boolean = true,
    /** 是否启用本地结果缓存（按 bvid+cid+模型+Prompt 版本+字幕哈希 去重）。 */
    val cacheEnabled: Boolean = true,
    /** 是否在抽屉里显示「重新生成（忽略缓存）」。 */
    val allowForceRefresh: Boolean = true,
    /** 是否把总结自动写入 BiliPai 的视频笔记草稿。 */
    val saveAsNoteDraft: Boolean = true,

    // ---------------- ASR 兜底（无字幕视频）----------------

    /** 没有字幕时，是否用 ASR 转写音频再总结。默认关闭（会消耗 ASR 额度与流量）。 */
    val asrEnabled: Boolean = false,
    /** ASR 服务地址（OpenAI 兼容 /audio/transcriptions）。默认 Groq。 */
    val asrBaseUrl: String = "https://api.groq.com/openai/v1",
    val asrApiKey: String = "",
    val asrModel: String = "whisper-large-v3-turbo",
    /** 转写语言提示；留空自动检测。中文视频建议 zh。 */
    val asrLanguage: String = "zh",
    /** 单次上传上限。Groq 免费档 25MB，付费档 100MB。 */
    /** ASR 走 HTTP 代理（host:port）。Groq 在部分网络下直连 403，需要代理。留空=直连。 */
    val asrProxy: String = "",
    val asrMaxBytes: Long = 25L * 1024 * 1024,
    val asrTimeoutSeconds: Int = 180
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    /** ASR 是否可用：开关打开 + 地址/Key/模型齐全。 */
    val asrConfigured: Boolean
        get() = asrEnabled && asrApiKey.isNotBlank() && asrBaseUrl.isNotBlank() && asrModel.isNotBlank()

    /** 只保留非敏感字段，用于日志/诊断。 */
    fun safeDescription(): String = "provider=$providerId model=$model style=${style.id} " +
        "detail=${detailLevel.id} chunk=$chunkCharBudget concurrency=$maxConcurrency jsonMode=auto"
}
