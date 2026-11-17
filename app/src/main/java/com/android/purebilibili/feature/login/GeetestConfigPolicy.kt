package com.android.purebilibili.feature.login

import org.json.JSONObject

internal class GeetestConfigRejectedException(reason: String) :
    IllegalStateException("极验配置服务拒绝请求: $reason")

internal fun parseGeetestConfig(raw: String, gt: String, challenge: String): String {
    val payload = raw.trim().removePrefix("(").removeSuffix(")")
    val root = JSONObject(payload)
    val status = root.optString("status")
    if (status != "success") {
        // 配置服务拒绝与 JSON 解析失败分开描述，不输出完整响应及验证会话参数。
        val reason = root.optString("error").takeIf { it.isNotBlank() }
            ?: root.optString("message").takeIf { it.isNotBlank() }
            ?: status.ifBlank { "缺少状态" }
        throw GeetestConfigRejectedException(reason)
    }
    val data = root.optJSONObject("data") ?: error("极验配置缺少 data")
    return data.apply {
        put("gt", gt)
        put("challenge", challenge)
        put("offline", false)
        put("new_captcha", true)
        put("product", "bind")
        put("width", "100%")
        put("https", true)
        put("protocol", "https://")
    }.toString()
}
