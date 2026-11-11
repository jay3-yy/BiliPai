package com.bilipai.aisummary.data

import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SubtitleLine
import com.bilipai.aisummary.model.SummaryException
import com.bilipai.aisummary.util.HttpDownload
import com.bilipai.aisummary.util.HttpUpload
import com.bilipai.aisummary.util.InputNormalizer
import com.bilipai.aisummary.util.parseProxy
import org.json.JSONObject
import java.io.File

/**
 * B 站 CDN（upos-sz-mirror*.bilivideo.com）对移动端 UA 会拒绝带 `nbs=1` 的地址
 * （`deny-reason: 存在nbs参数`），桌面 UA 才放行。详见 [GroqAsrClient.downloadWithFallback]。
 */
private const val BILIBILI_CDN_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * 一条可下载的音轨（来自 B 站 DASH）。
 * 只保留 ASR 需要的字段，避免插件反向依赖宿主的播放器模型。
 */
data class AudioTrack(
    val url: String,
    val backupUrls: List<String> = emptyList(),
    val bandwidth: Int = 0,
    val mimeType: String = "",
    val codecs: String = "",
    /** 视频时长（秒），用于估算大小与校验。 */
    val durationSec: Double = 0.0
) {
    /** 估算字节数；bandwidth 是 bit/s。 */
    fun estimatedBytes(): Long =
        if (bandwidth > 0 && durationSec > 0) (bandwidth * durationSec / 8).toLong() else 0L
}

/**
 * 宿主提供音轨的方式。
 *
 * 为什么不自己在插件里请求 `playurl`：B 站取流需要 WBI 签名 + 登录态 + 风控绕过，
 * 宿主（BiliPai）已经有一套能用的实现，直接复用最稳。
 */
fun interface AudioTrackProvider {
    suspend fun audioTrack(bvid: String, cid: Long): AudioTrack?
}

/**
 * Groq Whisper 转写（OpenAI 兼容的 `/audio/transcriptions`）。
 *
 * 关键限制（来自官方文档）：
 * - 单文件上限 **25MB**（免费档），超了要自行切分或用 url 参数；
 * - 支持 flac/mp3/mp4/mpeg/mpga/m4a/ogg/wav/webm；
 * - `response_format=verbose_json` 会返回带 start/end 的 segments，正好用于章节时间戳。
 *
 * B 站 DASH 音频是 fMP4（`.m4s`，AAC），扩展名改成 `.m4a` 后 Groq 能正常识别。
 */
class GroqAsrClient(
    private val httpDownload: HttpDownload,
    private val httpUpload: HttpUpload,
    private val tempDir: File,
    private val cookieProvider: () -> String = { "" },
    /** 按代理创建 HttpGet；为 null 表示宿主不支持连通性测试。 */
    private val httpGetFactory: ((java.net.Proxy?) -> com.bilipai.aisummary.util.HttpGet)? = null
) {
    /**
     * 设置页「测试 ASR 连接」：拉一次 `{baseUrl}/models` 验证地址/Key/代理是否可用。
     * 不产生转写费用，也能验证「需要代理才能访问」这类网络问题。
     */
    fun testConnection(config: AiSummaryConfig): String {
        val factory = httpGetFactory ?: return "当前宿主不支持 ASR 连通性测试"
        if (!config.asrConfigured) return "ASR 未配置完整（需要开启开关并填 Key）"
        val proxy = parseProxy(config.asrProxy)
        val url = InputNormalizer.normalizeBaseUrl(config.asrBaseUrl) + "/models"
        return try {
            val resp = factory(proxy).get(
                url,
                mapOf(
                    "Authorization" to "Bearer ${config.asrApiKey}",
                    "Accept" to "application/json"
                )
            )
            when {
                resp.isSuccessful -> "ASR 连接成功"
                resp.code == 401 -> "ASR API Key 无效（401）"
                resp.code == 403 -> "ASR 被拒绝（403）：该地区可能无法直连，请填代理"
                else -> "ASR 连接失败：HTTP ${resp.code}"
            }
        } catch (e: Exception) {
            "ASR 连接失败：${e.javaClass.simpleName} ${e.message?.take(120).orEmpty()}"
        }
    }
    fun transcribe(
        config: AiSummaryConfig,
        track: AudioTrack,
        onProgress: (String) -> Unit = {}
    ): List<SubtitleLine> {
        require(config.asrConfigured) { "ASR 未配置完整" }
        val maxBytes = config.asrMaxBytes.coerceIn(1L * 1024 * 1024, 100L * 1024 * 1024)
        val estimated = track.estimatedBytes()
        if (estimated > maxBytes) {
            throw SummaryException(
                "音频约 ${estimated / 1024 / 1024}MB，超过 ASR 单次上限 ${maxBytes / 1024 / 1024}MB。" +
                    "建议换更短的视频，或把「ASR 单次上限」调大（Groq 付费档可到 100MB）。",
                retryable = false
            )
        }

        val proxy = parseProxy(config.asrProxy)
        tempDir.mkdirs()
        val ext = guessExtension(track)
        val audioFile = File.createTempFile("asr_", ".$ext", tempDir)
        try {
            onProgress("正在下载音频（ASR 兜底）…")
            val bytes = downloadWithFallback(track, audioFile, maxBytes, config.asrTimeoutSeconds, proxy)
            onProgress("音频下载完成（${bytes / 1024 / 1024}MB），上传 Groq 转写…")

            val url = InputNormalizer.normalizeBaseUrl(config.asrBaseUrl) + "/audio/transcriptions"
            val resp = httpUpload.upload(
                url = url,
                headers = mapOf("Authorization" to "Bearer ${config.asrApiKey}"),
                fileField = "file",
                fileName = "audio.$ext",
                file = audioFile,
                fileMediaType = mediaTypeFor(ext),
                fields = buildMap {
                    put("model", config.asrModel)
                    put("response_format", "verbose_json")
                    put("temperature", "0")
                    // 中文视频为主；留空则由模型自动检测
                    config.asrLanguage.takeIf { it.isNotBlank() }?.let { put("language", it) }
                },
                timeoutSeconds = config.asrTimeoutSeconds,
                proxy = proxy
            )
            if (!resp.isSuccessful) {
                throw SummaryException(mapAsrHttpError(resp.code, resp.body), retryable = resp.code in 429..599)
            }
            return parseVerboseJson(resp.body)
        } finally {
            runCatching { audioFile.delete() }
        }
    }

    private fun downloadWithFallback(
        track: AudioTrack,
        dest: File,
        maxBytes: Long,
        timeoutSeconds: Int,
        proxy: java.net.Proxy?
    ): Long {
        // B 站 CDN 必须带 Referer，否则 403。
        // UA 必须用桌面浏览器：宿主的 DASH 选线会把 baseUrl 换成 upos-sz-mirror*.bilivideo.com，
        // 这类地址带 nbs=1，B 站 CDN 对移动端 UA 直接返回 403（deny-reason: 存在nbs参数），
        // 桌面 UA 才放行；与宿主离线下载 buildBilibiliDownloadHeaders 的 UA 保持一致。
        val headers = mapOf(
            "Referer" to "https://www.bilibili.com/",
            "User-Agent" to BILIBILI_CDN_UA,
            "Cookie" to cookieProvider()
        )
        val candidates = listOf(track.url) + track.backupUrls
        var last: Exception? = null
        candidates.forEach { url ->
            try {
                return httpDownload.download(url, headers, dest, timeoutSeconds, maxBytes, proxy)
            } catch (e: Exception) {
                last = e
            }
        }
        throw SummaryException(
            "音频下载失败：${last?.message?.take(160).orEmpty()}",
            cause = last
        )
    }

    /** Groq verbose_json → 我们的字幕行；没有 segments 时退化为整段文本。 */
    fun parseVerboseJson(raw: String): List<SubtitleLine> {
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw SummaryException("ASR 返回内容不是合法 JSON", retryable = false)
        }
        val segments = root.optJSONArray("segments")
        if (segments != null && segments.length() > 0) {
            val out = ArrayList<SubtitleLine>(segments.length())
            for (i in 0 until segments.length()) {
                val s = segments.optJSONObject(i) ?: continue
                val text = s.optString("text").trim()
                if (text.isEmpty()) continue
                val start = s.optDouble("start", 0.0)
                val end = s.optDouble("end", start)
                out += SubtitleLine(fromSec = start, toSec = maxOf(start, end), content = text)
            }
            if (out.isNotEmpty()) return out
        }
        val text = root.optString("text").trim()
        if (text.isEmpty()) throw SummaryException("ASR 没有返回任何文字（可能是纯音乐/无人声）", retryable = false)
        return listOf(SubtitleLine(0.0, 0.0, text))
    }

    private fun guessExtension(track: AudioTrack): String {
        val mime = track.mimeType.lowercase()
        return when {
            mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("ogg") -> "ogg"
            mime.contains("flac") -> "flac"
            mime.contains("wav") -> "wav"
            else -> "m4a"
        }
    }

    private fun mediaTypeFor(ext: String): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    private fun mapAsrHttpError(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("").take(160)
        return when (code) {
            401 -> "Groq API Key 无效（401）"
            413 -> "音频超过服务端上限（413），请调小「ASR 单次上限」或换更短的视频"
            429 -> "Groq 限流（429），稍后重试 $detail"
            in 500..599 -> "Groq 服务暂时不可用（$code）"
            else -> "ASR 请求失败：HTTP $code $detail"
        }
    }
}
