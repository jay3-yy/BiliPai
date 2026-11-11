package com.bilipai.aisummary.settings

import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.ProviderTemplate
import com.bilipai.aisummary.util.InputNormalizer

/**
 * 内置服务商模板。全部走 OpenAI Chat Completions 兼容协议。
 *
 * 注意：模型名会随厂商更新，这里给的是编写时可用的常见值；
 * 设置页提供「测试连接」按钮，实际以用户账号下可用模型为准。
 * 只要接口兼容 /chat/completions，任何厂商都可以用「自定义」接进来。
 */
object ProviderTemplates {

    const val DEFAULT_ID = "deepseek"

    val ALL: List<ProviderTemplate> = listOf(
        ProviderTemplate(
            id = "deepseek",
            displayName = "DeepSeek（官方）",
            baseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            candidateModels = listOf("deepseek-chat", "deepseek-reasoner"),
            docsUrl = "https://api-docs.deepseek.com/",
            supportsJsonMode = true,
            suggestedConcurrency = 2,
            note = "性价比最高，中文总结质量稳定；reasoner 更慢更贵，长视频不建议。"
        ),
        ProviderTemplate(
            id = "siliconflow",
            displayName = "硅基流动 SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1",
            defaultModel = "deepseek-ai/DeepSeek-V3",
            candidateModels = listOf(
                "deepseek-ai/DeepSeek-V3",
                "Qwen/Qwen2.5-7B-Instruct",
                "Qwen/Qwen2.5-14B-Instruct"
            ),
            docsUrl = "https://docs.siliconflow.cn/",
            supportsJsonMode = true,
            suggestedConcurrency = 2,
            note = "有多个免费小模型，适合先跑通流程；免费模型并发与速率限制较严。"
        ),
        ProviderTemplate(
            id = "zhipu",
            displayName = "智谱 GLM（BigModel）",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            candidateModels = listOf("glm-4-flash", "glm-4-air", "glm-4-plus"),
            docsUrl = "https://docs.bigmodel.cn/",
            supportsJsonMode = true,
            suggestedConcurrency = 2,
            note = "glm-4-flash 长期免费，适合做默认兜底模型。"
        ),
        ProviderTemplate(
            id = "moonshot",
            displayName = "Moonshot Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
            candidateModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
            docsUrl = "https://platform.moonshot.cn/docs/",
            supportsJsonMode = true,
            suggestedConcurrency = 1,
            note = "长上下文好，但按量计费较贵；建议把 chunkCharBudget 调大、减少分段。"
        ),
        ProviderTemplate(
            id = "dashscope",
            displayName = "阿里云百炼（兼容模式）",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
            candidateModels = listOf("qwen-plus", "qwen-max", "qwen-long", "qwen-turbo"),
            docsUrl = "https://help.aliyun.com/zh/model-studio/",
            supportsJsonMode = true,
            suggestedConcurrency = 2,
            note = "qwen-long 支持超长上下文，可把分段预算调到 20000+ 字符。"
        ),
        ProviderTemplate(
            id = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            candidateModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
            docsUrl = "https://platform.openai.com/docs/api-reference/chat",
            supportsJsonMode = true,
            suggestedConcurrency = 2,
            note = "国内直连通常需要自备网络环境。"
        ),
        ProviderTemplate(
            id = "ollama",
            displayName = "Ollama（本地 / 局域网）",
            baseUrl = "http://192.168.1.100:11434/v1",
            defaultModel = "qwen2.5:7b",
            candidateModels = listOf("qwen2.5:7b", "qwen2.5:14b", "glm4:9b"),
            docsUrl = "https://github.com/ollama/ollama/blob/main/docs/openai.md",
            supportsJsonMode = false,
            suggestedConcurrency = 1,
            note = "完全离线、隐私最好；需把 baseUrl 改成手机可达的局域网地址，且速度取决于设备。"
        ),
        ProviderTemplate(
            id = "custom",
            displayName = "自定义（OpenAI 兼容）",
            baseUrl = "",
            defaultModel = "",
            candidateModels = emptyList(),
            // 自定义网关：先尝试 json_object，被 400 拒绝时 ChatCompletionsClient 会自动去掉重发，
            // 因此这里保持 true 可拿到更严格的 JSON，同时不影响不支持的网关。
            supportsJsonMode = true,
            suggestedConcurrency = 2,
            note = "填任意兼容 /chat/completions 的地址，例如 One-API、New API、OpenRouter 中转。"
        )
    )

    fun byId(id: String): ProviderTemplate = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /**
     * 实际生效的服务商标识。
     *
     * 用户常常在某个模板（如 DeepSeek）上直接改 Base URL 指向自建网关/中转，
     * 此时 providerId 仍是旧模板，会误导显示，也影响 [ProviderTemplate.supportsJsonMode] 的判断。
     * 规则：Base URL 与所选模板不一致 → 视为 custom。
     */
    fun resolveEffectiveProviderId(config: AiSummaryConfig): String {
        val template = byId(config.providerId)
        if (template.id == CUSTOM_ID) return CUSTOM_ID
        val templateBase = InputNormalizer.normalizeBaseUrl(template.baseUrl)
        val actualBase = InputNormalizer.normalizeBaseUrl(config.baseUrl)
        return if (templateBase.equals(actualBase, ignoreCase = true)) template.id else CUSTOM_ID
    }

    /** 界面上展示的服务商名（跟随实际 Base URL，而不是残留的模板 id）。 */
    fun displayNameFor(config: AiSummaryConfig): String {
        val effective = resolveEffectiveProviderId(config)
        return if (effective == CUSTOM_ID) {
            "自定义（OpenAI 兼容）"
        } else {
            byId(effective).displayName
        }
    }

    /** 选中模板时生成一份配置；保留用户已填的 Key，避免切换模板时被清空。 */
    fun applyTemplate(current: AiSummaryConfig, templateId: String): AiSummaryConfig {
        val t = byId(templateId)
        return current.copy(
            providerId = t.id,
            baseUrl = t.baseUrl,
            model = t.defaultModel,
            maxConcurrency = t.suggestedConcurrency.coerceAtLeast(1)
        )
    }

    /** 供诊断展示：只列 id 与模型，绝不触碰 Key。 */
    fun describe(config: AiSummaryConfig): String = config.safeDescription()

    const val CUSTOM_ID = "custom"
}
