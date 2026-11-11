package com.bilipai.aisummary

import com.bilipai.aisummary.data.AudioTrack
import com.bilipai.aisummary.data.GroqAsrClient
import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SubtitleLine
import com.bilipai.aisummary.model.SummaryException
import com.bilipai.aisummary.util.HttpDownload
import com.bilipai.aisummary.util.HttpUpload
import com.bilipai.aisummary.util.HttpResponse
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AsrTest {

    private fun tempDir(): File = Files.createTempDirectory("asr-test").toFile()

    private fun client(
        dir: File,
        downloadBytes: Long = 1024L,
        uploadResponse: () -> HttpResponse = { HttpResponse(200, verboseJson()) }
    ): GroqAsrClient {
        val download = HttpDownload { _, _, dest, _, _, _ ->
            dest.writeBytes(ByteArray(downloadBytes.toInt()))
            downloadBytes
        }
        val upload = HttpUpload { _, _, fileField, fileName, file, _, fields, _, _ ->
            // 校验调用方确实按 Groq 协议构造
            assertEquals("file", fileField)
            assertEquals("whisper-large-v3-turbo", fields["model"])
            assertEquals("verbose_json", fields["response_format"])
            assertTrue(file.exists())
            uploadResponse()
        }
        return GroqAsrClient(download, upload, dir)
    }

    private fun verboseJson(): String = JSONObject().apply {
        put("text", "你好世界")
        put("segments", org.json.JSONArray().apply {
            put(JSONObject().put("start", 0.0).put("end", 2.4).put("text", " 你好"))
            put(JSONObject().put("start", 2.4).put("end", 5.1).put("text", "世界 "))
        })
    }.toString()

    private fun asrConfig(): AiSummaryConfig = AiSummaryConfig(
        asrEnabled = true,
        asrApiKey = "gsk_test",
        asrBaseUrl = "https://api.groq.com/openai/v1",
        asrModel = "whisper-large-v3-turbo"
    )

    private fun track(bandwidth: Int = 64000, duration: Double = 600.0) = AudioTrack(
        url = "https://cdn.example/a.m4s",
        bandwidth = bandwidth,
        mimeType = "audio/mp4",
        durationSec = duration
    )

    @Test
    fun `verbose json segments become subtitle lines with timestamps`() {
        val dir = tempDir()
        val lines = client(dir).transcribe(asrConfig(), track())
        assertEquals(2, lines.size)
        assertEquals(0.0, lines[0].fromSec, 0.001)
        assertEquals("你好", lines[0].content)
        assertEquals(2.4, lines[1].fromSec, 0.001)
        assertEquals("世界", lines[1].content)
        dir.deleteRecursively()
    }

    @Test
    fun `oversized audio is rejected before download`() {
        val dir = tempDir()
        // 192kbps * 3600s ≈ 82MB > 25MB
        val e = runCatching {
            client(dir).transcribe(asrConfig(), track(bandwidth = 192000, duration = 3600.0))
        }
        assertTrue(e.isFailure)
        val msg = e.exceptionOrNull()!!.message.orEmpty()
        assertTrue("应提示超限，实际：$msg", msg.contains("超过 ASR 单次上限"))
        dir.deleteRecursively()
    }

    @Test
    fun `audio download uses desktop UA so upos nbs urls are accepted`() {
        val dir = tempDir()
        var ua = ""
        val download = HttpDownload { _, headers, dest, _, _, _ ->
            ua = headers["User-Agent"].orEmpty()
            dest.writeBytes(ByteArray(16))
            16L
        }
        val upload = HttpUpload { _, _, _, _, _, _, _, _, _ -> HttpResponse(200, verboseJson()) }
        GroqAsrClient(download, upload, dir).transcribe(asrConfig(), track())
        assertTrue("下载 UA 应为桌面浏览器，实际：$ua", ua.contains("Windows NT"))
        assertFalse("下载 UA 不应是移动端，实际：$ua", ua.contains("Android"))
        dir.deleteRecursively()
    }

    @Test
    fun `http 401 maps to actionable message`() {
        val dir = tempDir()
        val e = runCatching {
            client(dir) { HttpResponse(401, """{"error":{"message":"invalid api key"}}""") }
                .transcribe(asrConfig(), track())
        }
        assertTrue(e.isFailure)
        assertTrue(e.exceptionOrNull()!!.message!!.contains("API Key 无效"))
        dir.deleteRecursively()
    }

    @Test
    fun `missing segments falls back to whole text`() {
        val dir = tempDir()
        val lines = client(dir) { HttpResponse(200, """{"text":"整段文本"}""") }
            .transcribe(asrConfig(), track())
        assertEquals(1, lines.size)
        assertEquals("整段文本", lines[0].content)
        dir.deleteRecursively()
    }

    @Test
    fun `empty transcription is a non retryable error`() {
        val dir = tempDir()
        val e = runCatching {
            client(dir) { HttpResponse(200, """{"text":""}""") }.transcribe(asrConfig(), track())
        }
        assertTrue(e.isFailure)
        assertFalse((e.exceptionOrNull() as SummaryException).retryable)
        dir.deleteRecursively()
    }

    @Test
    fun `asr source label is distinguished from plain model and cache`() {
        val base = com.bilipai.aisummary.model.SummaryResult(
            bvid = "BV1", cid = 1, title = "t",
            style = com.bilipai.aisummary.model.SummaryStyle.FULL_NOTE,
            providerId = "custom", model = "gemini-web/gemini-3.8-flash",
            createdAtMillis = 1_700_000_000_000L, overview = "o", asrUsed = true
        )
        assertEquals("〔ASR〕", base.sourceShortTag)
        assertTrue(base.sourceLabel.contains("ASR 转写字幕"))
        // 缓存回读后仍保留 ASR 标记
        val cached = base.copy(source = com.bilipai.aisummary.model.SummaryResult.Source.CACHE)
        assertEquals("〔缓存〕", cached.sourceShortTag)
        assertTrue(cached.sourceLabel.contains("ASR 转写字幕"))
    }
}
