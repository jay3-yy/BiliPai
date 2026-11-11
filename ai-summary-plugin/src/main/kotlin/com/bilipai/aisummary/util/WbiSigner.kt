package com.bilipai.aisummary.util

import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * B 站 WBI 签名（用于 /x/player/wbi/v2 与 /x/web-interface/view/conclusion/get）。
 *
 * 参考：bilibili-API-collect docs/misc/sign/wbi.md
 * 流程：nav 接口取 img_key/sub_key → 按固定混淆表生成 mixin_key(32) →
 *       参数加 wts、过滤特殊字符、按 key 排序、URL 编码 → md5(query + mixin_key) = w_rid。
 *
 * 纯 Kotlin 实现，只依赖 org.json（Android 内置），可在 JVM 单测里注入假 HttpGet。
 */
class WbiSigner(
    private val httpGet: HttpGet,
    private val cookieProvider: () -> String = { "" },
    private val clock: ClockSeconds = SystemClockSeconds
) {
    @Volatile
    private var cached: Pair<String, String>? = null
    @Volatile
    private var cachedAtSec: Long = 0L

    /** 返回可直接拼到 URL 上的 query（含 w_rid & wts）。 */
    fun signedQuery(params: Map<String, String>): String {
        val (img, sub) = keys()
        val mixinKey = mixinKey(img, sub)
        val signed = LinkedHashMap<String, String>(params).apply {
            put("wts", clock.now().toString())
        }
        val filtered = signed.mapValues { (_, v) -> FILTER_REGEX.replace(v, "") }
        val query = filtered.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val wRid = md5(query + mixinKey)
        return "$query&w_rid=$wRid"
    }

    /** 给已排序好的参数补签名并返回完整参数 Map（供需要 Map 的调用方使用）。 */
    fun sign(params: Map<String, String>): Map<String, String> {
        val q = signedQuery(params)
        return q.split('&').associate {
            val i = it.indexOf('=')
            if (i < 0) it to "" else it.substring(0, i) to it.substring(i + 1)
        }
    }

    @Synchronized
    private fun keys(): Pair<String, String> {
        val now = clock.now()
        cached?.let { if (now - cachedAtSec < KEY_TTL_SEC) return it }
        val resp = httpGet.get(
            NAV_URL,
            mapOf(
                "User-Agent" to UA,
                "Referer" to REFERER,
                "Cookie" to cookieProvider()
            )
        )
        if (!resp.isSuccessful) throw IllegalStateException("nav 接口失败: HTTP ${resp.code}")
        val wbi = JSONObject(resp.body)
            .optJSONObject("data")
            ?.optJSONObject("wbi_img")
            ?: throw IllegalStateException("nav 未返回 wbi_img")
        val img = fileNameWithoutExt(wbi.optString("img_url"))
        val sub = fileNameWithoutExt(wbi.optString("sub_url"))
        if (img.isBlank() || sub.isBlank()) throw IllegalStateException("wbi_img 字段为空")
        val pair = img to sub
        cached = pair
        cachedAtSec = now
        return pair
    }

    private fun fileNameWithoutExt(url: String): String =
        url.substringAfterLast('/').substringBeforeLast('.')

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun md5(s: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        const val REFERER = "https://www.bilibili.com/"
        const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val KEY_TTL_SEC = 6 * 3600L
        private val FILTER_REGEX = Regex("[!'()*]")

        private val MIXIN_KEY_ENC_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        )

        /** 公开以便单元测试覆盖这个最容易写错的环节。 */
        fun mixinKey(imgKey: String, subKey: String): String {
            val raw = imgKey + subKey
            require(raw.length >= MIXIN_KEY_ENC_TAB.size) { "img_key + sub_key 长度不足 64" }
            return StringBuilder(32).apply {
                for (i in 0 until 32) append(raw[MIXIN_KEY_ENC_TAB[i]])
            }.toString()
        }
    }
}
