package com.bilipai.aisummary.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 极简 HTTP 抽象：把网络层与业务逻辑解耦，便于在 JVM 上注入假实现做单元测试。 */
fun interface HttpGet {
    @Throws(Exception::class)
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

fun interface HttpPost {
    @Throws(Exception::class)
    fun post(url: String, headers: Map<String, String>, body: String, timeoutSeconds: Int): HttpResponse
}

/**
 * 把远端音频流式下载到本地文件（ASR 用）。
 * 返回实际写入字节数；调用方负责删除临时文件。
 */
fun interface HttpDownload {
    @Throws(Exception::class)
    fun download(
        url: String,
        headers: Map<String, String>,
        dest: java.io.File,
        timeoutSeconds: Int,
        maxBytes: Long,
        proxy: java.net.Proxy?
    ): Long
}

/**
 * multipart/form-data 上传（ASR 转写用）。
 * [fileField] 为文件字段名，[fields] 为普通文本字段。
 */
fun interface HttpUpload {
    @Throws(Exception::class)
    fun upload(
        url: String,
        headers: Map<String, String>,
        fileField: String,
        fileName: String,
        file: java.io.File,
        fileMediaType: String,
        fields: Map<String, String>,
        timeoutSeconds: Int,
        proxy: java.net.Proxy?
    ): HttpResponse
}

data class HttpResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * 基于 OkHttp 的默认实现。BiliPai 已依赖 OkHttp，直接复用即可；
 * 若宿主用别的网络栈，实现 [HttpGet]/[HttpPost] 两个接口注入就行。
 *
 * 局域网 LLM 网关（Ollama / LM Studio / vLLM 等）常见坑：手机有 IPv6 地址时
 * OkHttp 会优先解析 AAAA，而网关往往只监听 IPv4，表现为「连接超时 / failed to connect」。
 * 因此这里把 Dns 限制为只返回 IPv4，保证局域网 Base URL 可用。
 */
class OkHttpClientProvider(
    private val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient()
) : HttpGet, HttpPost, HttpDownload, HttpUpload {

    private fun ipv4FirstClient() = client.newBuilder()
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val all = try {
                    java.net.InetAddress.getAllByName(hostname).toList()
                } catch (e: Exception) {
                    emptyList()
                }
                val v4 = all.filterIsInstance<java.net.Inet4Address>()
                // 有 IPv4 就只用 IPv4；纯 IPv6 主机仍保持可用。
                return v4.ifEmpty { all }
            }
        })
        .build()

    override fun get(url: String, headers: Map<String, String>): HttpResponse =
        get(url, headers, null)

    /** 带代理的 GET（ASR 连通性测试等场景用）。 */
    fun get(url: String, headers: Map<String, String>, proxy: java.net.Proxy?): HttpResponse {
        val builder = ipv4FirstClient().newBuilder()
        if (proxy != null) builder.proxy(proxy)
        val req = okhttp3.Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> if (v.isNotBlank()) addHeader(k, v) }
        }.build()
        builder.build().newCall(req).execute().use { resp ->
            return HttpResponse(resp.code, resp.body.string())
        }
    }

    override fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutSeconds: Int
    ): HttpResponse {
        val timed = ipv4FirstClient().newBuilder()
            .withTimeouts(timeoutSeconds)
            .build()
        val req = okhttp3.Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { headers.forEach { (k, v) -> if (v.isNotBlank()) addHeader(k, v) } }
            .build()
        timed.newCall(req).execute().use { resp ->
            return HttpResponse(resp.code, resp.body.string())
        }
    }

    /**
     * 统一应用超时。[timeoutSeconds] 只设 callTimeout 时，OkHttp 的 readTimeout
     * 仍是默认 10s：非流式 LLM 生成在首字节返回前会静默等待，10s 就被判超时，
     * 表现为「模型明明可用，但详细总结必失败」。这里把 read/write 一起放宽。
     */
    private fun okhttp3.OkHttpClient.Builder.withTimeouts(timeoutSeconds: Int) = this
        .callTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15L, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)

    /** 流式下载到文件，超过 [maxBytes] 立即中断，避免把手机内存/存储打满。 */
    override fun download(
        url: String,
        headers: Map<String, String>,
        dest: java.io.File,
        timeoutSeconds: Int,
        maxBytes: Long,
        proxy: java.net.Proxy?
    ): Long {
        val builder = ipv4FirstClient().newBuilder().withTimeouts(timeoutSeconds)
        if (proxy != null) builder.proxy(proxy)
        val timed = builder.build()
        val req = okhttp3.Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> if (v.isNotBlank()) addHeader(k, v) }
        }.build()
        timed.newCall(req).execute().use { resp ->
            if (resp.code !in 200..299) {
                throw java.io.IOException("音频下载失败：HTTP ${resp.code}")
            }
            val declared = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            if (declared > maxBytes) {
                throw java.io.IOException("音频文件 ${declared / 1024 / 1024}MB 超过上限 ${maxBytes / 1024 / 1024}MB")
            }
            val sink = dest.outputStream()
            var written = 0L
            sink.use { out ->
                val buf = ByteArray(64 * 1024)
                resp.body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        written += n
                        if (written > maxBytes) {
                            throw java.io.IOException("音频超过上限 ${maxBytes / 1024 / 1024}MB")
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            return written
        }
    }

    override fun upload(
        url: String,
        headers: Map<String, String>,
        fileField: String,
        fileName: String,
        file: java.io.File,
        fileMediaType: String,
        fields: Map<String, String>,
        timeoutSeconds: Int,
        proxy: java.net.Proxy?
    ): HttpResponse {
        val multipart = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
        fields.forEach { (k, v) -> multipart.addFormDataPart(k, v) }
        multipart.addFormDataPart(
            fileField, fileName,
            file.asRequestBody(fileMediaType.toMediaType())
        )
        val builder = ipv4FirstClient().newBuilder().withTimeouts(timeoutSeconds)
        if (proxy != null) builder.proxy(proxy)
        val timed = builder.build()
        val req = okhttp3.Request.Builder()
            .url(url)
            .post(multipart.build())
            .apply { headers.forEach { (k, v) -> if (v.isNotBlank()) addHeader(k, v) } }
            .build()
        timed.newCall(req).execute().use { resp ->
            return HttpResponse(resp.code, resp.body.string())
        }
    }
}

/** 返回当前 Unix 秒，抽出来便于测试固定时间。 */
fun interface ClockSeconds { fun now(): Long }

object SystemClockSeconds : ClockSeconds {
    override fun now(): Long = System.currentTimeMillis() / 1000L
}

/** 把 `host:port` 解析成 [java.net.Proxy]；空串或格式错误返回 null（直连）。 */
fun parseProxy(spec: String): java.net.Proxy? {
    val s = spec.trim()
    if (s.isEmpty()) return null
    val host = s.substringBeforeLast(':', s)
    val port = s.substringAfterLast(':', "").toIntOrNull() ?: return null
    if (host.isBlank() || port !in 1..65535) return null
    return java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(host, port))
}
