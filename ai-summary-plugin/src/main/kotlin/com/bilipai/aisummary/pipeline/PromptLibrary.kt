package com.bilipai.aisummary.pipeline

import com.bilipai.aisummary.model.SubtitleChunk
import com.bilipai.aisummary.model.SummaryDetailLevel
import com.bilipai.aisummary.model.SummaryStyle

/**
 * Prompt 库。
 *
 * 设计参考了多个开源 B 站总结项目的共性结构（角色约束 → 带时间戳的字幕输入 →
 * 严格 JSON 输出 → 防幻觉 → 章节/术语/金句 → 观看建议），并按 map-reduce 拆成两段：
 *   MAP   : 单个字幕分段 → 该段要点 + 时间点
 *   REDUCE: 汇总所有分段 → 总览 + 章节 + 术语 + 金句 + 观看建议
 *
 * 调研参考（原文见 reference/lead/prompts/）：
 * - coztde/bili-ai-summarizer `src/pipeline/summary.ts`：严格依据字幕、ASR 错词只在
 *   高置信度时修正、作者观点用「作者认为」、章节按话题而非固定时长切分、
 *   金句忠实原文、时间戳必须来自原始字幕且做 grounded 校验、禁止输出未知字段。
 * - tututuhehehe/ai-web-summary `bili_src/07-chat-summary.js`：字幕 + 连续追问、
 *   token 用量统计、会话级上下文。
 * 本实现吸收了上述约束，但按 map-reduce 拆成两段，并保留更轻量的输出结构
 * （不生成思维导图 / quality 字段），以便直接渲染成底部抽屉与笔记草稿。
 *
 * 三条硬约束（实测最容易翻车的地方）：
 * 1. 只允许使用字幕中出现的信息，不得引入外部知识或编造数据；
 * 2. 所有时间戳必须来自输入，格式统一为 mm:ss 或 hh:mm:ss；
 * 3. 只输出 JSON，不要 Markdown 代码围栏、不要解释性前后缀。
 *
 * 修改任何 Prompt 时请同步提升 PROMPT_VERSION，否则缓存会返回旧格式的结果。
 */
object PromptLibrary {

    const val PROMPT_VERSION = 8

    private const val COMMON_RULES = """
你是「B 站视频笔记助手」。你的任务是只根据给出的视频字幕，生成结构化的中文笔记。

硬性规则：
- 只能使用字幕里明确出现的信息。不得编造人名、数据、结论、时间点，不得补充外部知识。
- 字幕里没有的内容，不要写进笔记；不确定的地方宁可不写，也不要猜。
- 每个要点都要能对应到字幕中的某个时间位置；输出的时间戳必须来自输入中出现的 [mm:ss]，禁止估算、平均分配或生成不存在的时间。
- 不要复述片头寒暄、广告、求三连、片尾引导等无信息量内容（识别广告时可以概括为「口播广告」）。
- 字幕可能有 ASR 错词、同音字、断句错误和中英文混杂：只有上下文高度确定时才修正，无法确认时不要造新术语。
- 作者没有明确下结论时不要替作者下结论；作者的个人观点用「作者认为」表述，不要改写成客观事实。
- 章节必须按话题切换划分（新问题 / 新观点 / 新案例 / 新方法），禁止按固定分钟数或平均比例切分、禁止为凑数量强行拆分。
- 金句必须忠实原文，只可去掉语气词与重复词；没有合适引用时返回空数组，不要硬凑。
- 语言与视频主要语言保持一致；中文视频用简体中文，专有名词保留原文。
- 只输出一个 JSON 对象，严格使用指定字段，不要输出 ```json 围栏、解释文字、源字幕或思考过程。
"""

    private const val MAP_SCHEMA = """
输出 JSON 格式（严格遵守）：
{
  "points": ["[mm:ss] 该段要点1", "[mm:ss] 该段要点2"],
  "timestamps": ["mm:ss", "mm:ss"]
}
points 条数以系统提示里的「详略」要求为准；每条一句话、信息密度高。
每条要点**必须**以它对应的那行字幕的 [mm:ss] 开头——用该要点真正讲到的位置，
不要整段都用片段起点。timestamps 为该段最值得回看的 1~5 个时间点。
"""

    private const val REDUCE_SCHEMA = """
输出 JSON 格式（严格遵守）：
{
  "overview": "3~5 句话说明这个视频讲了什么、核心结论是什么",
  "chapters": [
    {"title": "章节标题", "start": "mm:ss", "end": "mm:ss", "points": ["[mm:ss] 要点1", "[mm:ss] 要点2"]}
  ],
  "terms": ["术语 / 概念（附一句字幕中的解释）"],
  "quotes": ["金句或关键论断（原文，不要改写）"],
  "verdict": "一句话：适合谁看、是否值得看完"
}
chapters 按时间正序，start 必须是分段摘要里出现过的时间戳；
每条要点必须原样保留它在逐段摘要里的 [mm:ss] 前缀（不要改写、不要省略），UI 用它做逐条跳转；
terms 与 quotes 若字幕中没有合适内容，返回空数组，不要硬凑。
"""

    fun systemMap(
        style: SummaryStyle,
        detail: SummaryDetailLevel = SummaryDetailLevel.STANDARD
    ): String = COMMON_RULES.trim() + "\n\n" + when (style) {
        SummaryStyle.QUICK -> "当前模式「快速总结」：只提炼最有价值的结论，忽略细节与举例。\n\n"
        SummaryStyle.OUTLINE -> "当前模式「章节大纲」：重点识别话题切换的位置，为每个话题给出可概括的标题。\n\n"
        SummaryStyle.FULL_NOTE -> "当前模式「完整笔记」：保留论证过程、关键数据、步骤与例子，不要过度压缩。\n\n"
    } + mapDetailRule(detail) + MAP_SCHEMA.trim()

    fun userMap(title: String, chunk: SubtitleChunk, style: SummaryStyle): String = buildString {
        append("视频标题：").append(title).append('\n')
        append("当前片段：第 ").append(chunk.index + 1).append('/').append(chunk.total)
            .append(" 段，时间范围 ")
            .append(SubtitleChunk.formatTime(chunk.startSec)).append(" ~ ")
            .append(SubtitleChunk.formatTime(chunk.endSec)).append('\n')
        append("注意：片段首尾可能与相邻片段有少量重叠，重复内容只记一次。\n\n")
        append("字幕（每行前的方括号是该行起始时间）：\n")
        append(chunk.toPromptText())
        append("\n请按系统提示的 JSON 格式输出该段要点（模式：").append(style.displayName).append("）。")
    }

    fun systemReduce(
        style: SummaryStyle,
        detail: SummaryDetailLevel = SummaryDetailLevel.STANDARD
    ): String = COMMON_RULES.trim() + "\n\n" +
        "你现在要把「逐段要点」合并成一份完整笔记。要点已经过压缩，请勿再引入新信息。\n\n" +
        when (style) {
            SummaryStyle.QUICK -> "「快速总结」模式：overview 控制在 3 句以内，chapters 最多 4 个，terms/quotes 可以为空。\n\n"
            SummaryStyle.OUTLINE -> "「章节大纲」模式：chapters 要覆盖全程、标题精炼，overview 只做一句话导语。\n\n"
            SummaryStyle.FULL_NOTE -> "「完整笔记」模式：chapters 覆盖全程且每个章节 2~5 条要点，尽量保留术语与原文金句。\n\n"
        } + reduceDetailRule(detail) + REDUCE_SCHEMA.trim() + "\n\n" +
        if (detail == SummaryDetailLevel.BRIEF) REDUCE_COVERAGE_RULE_BRIEF else REDUCE_COVERAGE_RULE

    /** MAP 阶段的详略约束：直接决定单段输出多少条要点。 */
    private fun mapDetailRule(detail: SummaryDetailLevel): String = when (detail) {
        SummaryDetailLevel.BRIEF ->
            "详略「精简」（优先于风格）：本段只保留 2~4 条最重要结论，忽略举例、铺垫与重复解释。\n\n"
        SummaryDetailLevel.STANDARD ->
            "详略「标准」：本段保留 3~8 条要点，覆盖主要论点与关键数据。\n\n"
        SummaryDetailLevel.DETAILED ->
            "详略「详细」（优先于风格）：本段保留 6~12 条要点，尽量保留论证步骤、关键数据、例子与值得引用的原话。\n\n"
    }

    /** REDUCE 阶段的详略约束：决定 overview 长度、章节数与每章要点数。 */
    private fun reduceDetailRule(detail: SummaryDetailLevel): String = when (detail) {
        SummaryDetailLevel.BRIEF ->
            "详略「精简」（优先于风格）：overview 2~3 句；chapters 2~4 个，每章 1~2 条要点；terms/quotes 可以为空。\n\n"
        SummaryDetailLevel.STANDARD ->
            "详略「标准」：overview 3~5 句；chapters 数量接近输入段数，每章 2~5 条要点。\n\n"
        SummaryDetailLevel.DETAILED ->
            "详略「详细」（优先于风格）：overview 5~8 句；chapters 数量接近输入段数，每章 3~6 条要点；" +
                "terms 尽量列全，quotes 保留 2~5 条原文。\n\n"
    }

    /** 精简模式允许合并相邻话题，不强制每段都单独成章。 */
    private const val REDUCE_COVERAGE_RULE_BRIEF = """
覆盖度要求（精简模式）：
- 可以合并相邻话题，但不要漏掉视频的主线结论。
- 每个章节的 start 取自它所覆盖的最早那一段的时间戳。
"""

    /**
     * REDUCE 阶段的覆盖度约束。
     *
     * 实测（8 段输入的 40 分钟视频）：
     * 不加这条时会过度压缩成 2 个章节、丢掉中段信息；
     * 加上后输出 4 个章节、8 段全部被覆盖。这是影响笔记质量最大的一条约束。
     */
    private const val REDUCE_COVERAGE_RULE = """
覆盖度要求（重要）：
- 输入的**每一段**要点都必须被某个章节覆盖，不得因为合并而丢掉某一段的信息。
- 章节数量应接近输入段数（大约每 1~2 段对应 1 个章节），不要过度压缩成两三个大章节。
- 每个章节的 start 取自它所覆盖的最早那一段的时间戳。
"""

    /**
     * 模型输出被长度限制截断时的重试约束（追加到 system prompt 末尾）。
     *
     * 实测：自定义网关（gemini-web 等）对输出长度有隐含上限，「详细」档要求
     * 6~12 条要点时 JSON 会断在半截，解析失败后旧逻辑会把原始 JSON 当正文。
     * 这里要求模型压缩成一份一定能解析完的 JSON，宁可少写也不能断。
     */
    const val COMPACT_RETRY_RULE = """
上一次输出因为长度限制被截断，JSON 不完整。这次必须输出完整、可解析的 JSON：
- chapters 最多 4 个，每个章节最多 3 条要点；
- terms 与 quotes 返回空数组；
- overview 最多 4 句话；
- 不要在 JSON 前后输出任何文字，也不要省略右花括号。
"""

    fun userReduce(title: String, segments: List<String>): String = buildString {
        append("视频标题：").append(title).append('\n')
        append("共 ").append(segments.size).append(" 段逐段要点，按时间顺序如下：\n\n")
        segments.forEachIndexed { i, s ->
            append("--- 第 ").append(i + 1).append(" 段 ---\n").append(s.trim()).append("\n\n")
        }
        append("请合并为一份完整笔记，按系统提示的 JSON 格式输出。记住：时间戳只能取自上面的要点。")
    }
}
