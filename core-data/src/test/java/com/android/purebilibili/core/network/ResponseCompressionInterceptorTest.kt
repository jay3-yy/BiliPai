package com.android.purebilibili.core.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.decodeHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class ResponseCompressionInterceptorTest {
    private val json = "{\"code\":0,\"message\":\"ok\"}"

    @Test
    fun negotiatesAndDecodesBrotliResponse() {
        val bytes = "0b0c807b22636f6465223a302c226d657373616765223a226f6b227d03".decodeHex().toByteArray()
        val (sent, response) = exchange(Request.Builder(), bytes, "br")
        response.use {
            assertEquals("br, gzip", sent.header("Accept-Encoding"))
            assertEquals(json, it.body.string())
            assertNull(it.header("Content-Encoding"))
            assertNull(it.header("Content-Length"))
        }
    }

    @Test
    fun stillDecodesGzipResponse() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(json.toByteArray()) }
        val (_, response) = exchange(Request.Builder(), output.toByteArray(), "gzip")
        response.use { assertEquals(json, it.body.string()) }
    }

    @Test
    fun preservesExplicitIdentityAndRangeRequests() {
        val requests = listOf(
            Request.Builder().header("Accept-Encoding", "identity"),
            Request.Builder().header("Range", "bytes=0-23"),
        )
        for (request in requests) {
            val original = request.url("https://example.test/content").build()
            val (sent, response) = exchange(request, json.toByteArray(), null)
            response.use {
                assertEquals(original.header("Accept-Encoding"), sent.header("Accept-Encoding"))
                assertEquals(original.header("Range"), sent.header("Range"))
                assertEquals(json, it.body.string())
            }
        }
    }

    // 终端拦截器提供真实压缩字节，验证协商与流式解码，不依赖外部服务。
    private fun exchange(
        builder: Request.Builder,
        bytes: ByteArray,
        encoding: String?,
    ): Pair<Request, Response> {
        lateinit var sent: Request
        val client = OkHttpClient.Builder()
            .addInterceptor(ResponseCompressionInterceptor)
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(sent)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Length", bytes.size.toString())
                    .apply { if (encoding != null) header("Content-Encoding", encoding) }
                    .body(bytes.toResponseBody())
                    .build()
            }
            .build()
        val response = client.newCall(builder.url("https://example.test/content").build()).execute()
        return sent to response
    }
}
