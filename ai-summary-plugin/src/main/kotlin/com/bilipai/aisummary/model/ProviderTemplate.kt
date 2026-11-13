package com.bilipai.aisummary.model

/**
 * OpenAI Chat Completions 兼容服务的预设模板。
 *
 * 约定：baseUrl 一律写到 `/v1`（或厂商等价前缀），且不以 `/` 结尾；
 * [com.bilipai.aisummary.data.ChatCompletionsClient] 会自行拼接 `/chat/completions`。
 * 用户可在设置页把任意一项覆盖成自定义值，模板只是「一键填好」的快捷方式。
 */
data class ProviderTemplate(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val candidateModels: List<String> = emptyList(),
    val docsUrl: String = "",
    /** 是否支持 response_format=json_object；不支持的服务会自动退回纯文本 + 容错解析。 */
    val supportsJsonMode: Boolean = true,
    /** 该服务建议的并发上限，避免免费/低价额度被瞬间打满。 */
    val suggestedConcurrency: Int = 2,
    val note: String = ""
)
