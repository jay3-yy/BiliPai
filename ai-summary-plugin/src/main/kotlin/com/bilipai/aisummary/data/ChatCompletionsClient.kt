package com.bilipai.aisummary.data

import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SummaryException
import com.bilipai.aisummary.settings.ProviderTemplates
import com.bilipai.aisummary.util.HttpGet
import com.bilipai.aisummary.util.InputNormalizer
import com.bilipai.aisummary.util.HttpPost
import com.bilipai.aisummary.util.HttpResponse
import org.json.JSONObject

data class ChatResult(
    val content: String,
    val promptTokens: Int,
    val completionTokens: Int
)

/**
 * OpenAI Chat Completions 兼容客户端。
 *
 * 设计取舍：
 * - 默认**非流式**（一次拿到完整 JSON，便于结构化解析与缓存）；SSE 流式留作后续扩展。
 * - 业务层与网络层通过 [HttpPost] 解耦，方便 JVM 单测注入假响应。
 * - 所有方法都是阻塞式，调用方必须放在 Dispatchers.IO / 后台线程。
 * - 错误信息绝不回显 apiKey。
 */
class ChatCompletionsClient(
    private val httpPost: HttpPost,
    private val httpGet: HttpGet? = null,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) {
    fun chat(
        config: AiSummaryConfig,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = config.maxTokensPerSegment,
        jsonMode: Boolean = true
    ): ChatResult {
        require(config.isConfigured) { "模型未配置完整" }
        // 以实际 Base URL 判定服务商：模板 id 可能残留（例如在 DeepSeek 模板上改成自建网关）
        val effectiveProviderId = ProviderTemplates.resolveEffectiveProviderId(config)
        var useJson = jsonMode && ProviderTemplates.byId(effectiveProviderId).supportsJsonMode
        // 中文输入法可能把 / : 输成全角字符，统一归一化后再拼 URL
        val baseUrl = InputNormalizer.normalizeBaseUrl(config.baseUrl)
        val url = "$baseUrl/chat/completions"
        val headers = mapOf(
            "Authorization" to "Bearer ${config.apiKey}",
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )

        var lastError: Exception? = null
        // 多留一次机会给「response_format 不被支持」的自动降级重发。
        val attempts = config.maxRetries + 1 + if (useJson) 1 else 0
        repeat(attempts) { attempt ->
            if (attempt > 0) sleeper(backoffMillis(attempt))
            val body = buildRequestBody(config, systemPrompt, userPrompt, maxTokens, useJson)
            try {
                val resp: HttpResponse = httpPost.post(url, headers, body, config.timeoutSeconds)
                if (resp.isSuccessful) return parseResponse(resp.body)
                // 部分兼容服务（尤其硅基流动/智谱部分模型）不支持 response_format，
                // 返回 400 时去掉该字段重发，靠 ResultParser 的兜底解析保证结果可用。
                if (resp.code == 400 && useJson && isUnsupportedJsonMode(resp.body)) {
                    useJson = false
                    lastError = null
                    return@repeat
                }
                val err = SummaryException(mapHttpError(resp), retryable = isRetryable(resp.code))
                lastError = err
                if (!err.retryable) throw err
            } catch (e: SummaryException) {
                if (!e.retryable) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = SummaryException(humanizeNetworkError(e, baseUrl))
            }
        }
        throw lastError ?: SummaryException("模型请求失败：未知错误")
    }

    /**
     * 把底层网络异常翻译成用户能看懂、能自救的提示。
     *
     * 实测最坑的一种：手机开着 VPN（如 Google 隐私保护 VPN）时，
     * `default dev ipsec1863` 会把局域网地址也拖进隧道，导致连接局域网 LLM 网关
     * 超时，而手机自带 shell 却能连通，容易误判成插件 bug。
     */
    private fun humanizeNetworkError(e: Exception, baseUrl: String): String {
        val raw = "${e.javaClass.simpleName}（${e.message?.take(120).orEmpty()}）"
        val isLanHost = Regex(
            "^https?://(192\\.168\\.|10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|localhost|127\\.0\\.0\\.1)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(baseUrl)
        val isTimeout = e is java.net.SocketTimeoutException ||
            e is java.net.ConnectException ||
            e.cause is java.net.SocketTimeoutException ||
            e.cause is java.net.ConnectException
        return if (isLanHost && isTimeout) {
            "无法连接到局域网模型地址 $baseUrl。\n" +
                "常见原因：①手机开着 VPN / 代理，把局域网流量也走了隧道（请临时关闭 VPN 后重试）；" +
                "②电脑防火墙拦截；③地址写错或服务未启动。\n原始错误：$raw"
        } else {
            "模型请求失败：$raw"
        }
    }

    /**
     * 拉取服务商真实模型列表（GET {baseUrl}/models）。
     * 模型名迭代很快，设置页提供这个按钮，避免把模板里的默认模型写死。
     */
    fun listModels(config: AiSummaryConfig): List<String> {
        val get = httpGet ?: return emptyList()
        val url = InputNormalizer.normalizeBaseUrl(config.baseUrl) + "/models"
        val resp = get.get(
            url,
            mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "Accept" to "application/json"
            )
        )
        if (!resp.isSuccessful) {
            throw SummaryException("拉取模型列表失败：HTTP ${resp.code}", retryable = false)
        }
        val arr = try {
            JSONObject(resp.body).optJSONArray("data")
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
            .distinct()
            .sorted()
    }

    /** 设置页「测试连接」：用极小请求验证 Key 与模型名是否可用，会消耗极少量额度。 */
    fun testConnection(config: AiSummaryConfig): String {
        val r = chat(
            config = config,
            systemPrompt = "你是一个连通性测试助手，只回复指定文字。",
            userPrompt = "请只回复：OK",
            maxTokens = 16,
            jsonMode = false
        )
        if (r.content.contains("OK", ignoreCase = true)) return "连接成功（${config.model}）"
        return "连接成功，但模型回复异常：${r.content.take(60)}"
    }

    /** 公开供测试：请求体构造。 */
    fun buildRequestBody(
        config: AiSummaryConfig,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        jsonMode: Boolean
    ): String = JSONObject().apply {
        put("model", config.model)
        // 推理/思考模型（deepseek-reasoner、o 系列等）会拒绝或忽略 temperature。
        if (!isReasoningModel(config.model)) put("temperature", config.temperature.toDouble())
        // OpenAI 新 o 系列只认 max_completion_tokens，其它兼容服务用 max_tokens。
        put(
            if (isOpenAiReasoningModel(config.model)) "max_completion_tokens" else "max_tokens",
            maxTokens
        )
        put("stream", false)
        put("messages", org.json.JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        })
        if (jsonMode) {
            put("response_format", JSONObject().put("type", "json_object"))
        }
    }.toString()

    private fun isUnsupportedJsonMode(body: String): Boolean {
        if (body.isBlank()) return false
        val s = body.lowercase()
        return listOf(
            "response_format", "json_object", "unsupported parameter", "not support",
            "不支持", "invalid parameter", "unexpected keyword"
        ).any { s.contains(it) }
    }

    private fun isReasoningModel(model: String): Boolean {
        val m = model.lowercase()
        return m.contains("reasoner") || m.contains("thinking") || isOpenAiReasoningModel(model)
    }

    private fun isOpenAiReasoningModel(model: String): Boolean =
        Regex("^o[1-9](?:-|$)").containsMatchIn(model.lowercase())

    private fun parseResponse(raw: String): ChatResult {
        val root = try { JSONObject(raw) } catch (e: Exception) {
            throw SummaryException("模型返回不是合法 JSON", retryable = false)
        }
        if (root.has("error")) {
            val msg = root.optJSONObject("error")?.optString("message").orEmpty()
            throw SummaryException("模型服务报错：${msg.take(200)}", retryable = false)
        }
        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        if (content.isBlank()) {
            // 部分推理模型把内容放在 reasoning_content 里
            val fallback = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("reasoning_content").orEmpty()
            if (fallback.isBlank()) throw SummaryException("模型返回内容为空", retryable = false)
            return ChatResult(fallback, 0, 0)
        }
        val usage = root.optJSONObject("usage")
        return ChatResult(
            content = content.trim(),
            promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0,
            completionTokens = usage?.optInt("completion_tokens", 0) ?: 0
        )
    }

    private fun mapHttpError(resp: HttpResponse): String {
        val detail = runCatching {
            JSONObject(resp.body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("").take(160)
        return when (resp.code) {
            401 -> "API Key 无效或已过期（401）"
            402, 403 -> "账户余额不足或无权访问该模型（${resp.code}）$detail"
            404 -> "模型不存在或 baseUrl 不正确（404），请检查是否漏了 /v1"
            429 -> "触发限流（429），稍后会自动重试；建议降低并发 $detail"
            in 500..599 -> "模型服务暂时不可用（${resp.code}），正在重试"
            else -> "模型请求失败：HTTP ${resp.code} $detail"
        }
    }

    private fun isRetryable(code: Int): Boolean =
        code == 429 || code == 408 || code in 500..599

    private fun backoffMillis(attempt: Int): Long =
        (500L shl ((attempt - 1).coerceAtMost(4))).coerceAtMost(8000L)
}
