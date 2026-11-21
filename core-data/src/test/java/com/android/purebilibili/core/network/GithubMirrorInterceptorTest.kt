package com.android.purebilibili.core.network

import com.android.purebilibili.core.network.policy.GithubMirrorSettings
import com.android.purebilibili.core.network.policy.GithubMirrorSource
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class GithubMirrorInterceptorTest {

    private val rawUrl = "https://raw.githubusercontent.com/jay3-yy/BiliPai/main/README.md"
    private val enabledSettings = GithubMirrorSettings(
        enabled = true,
        source = GithubMirrorSource.GHFAST,
    )

    @Test
    fun passesOriginalUrlThroughWhenDisabled() {
        val requestedUrls = mutableListOf<String>()
        val client = buildTestClient(GithubMirrorSettings(enabled = false)) { request ->
            requestedUrls += request.url.toString()
            okResponse(request, "direct")
        }

        client.newCall(Request.Builder().url(rawUrl).build()).execute().use { response ->
            assertEquals("direct", response.body.string())
        }
        assertEquals(listOf(rawUrl), requestedUrls)
    }

    @Test
    fun rewritesMirrorableGetRequestsAndAvoidsOriginOn200() {
        val requestedUrls = mutableListOf<String>()
        val client = buildTestClient(enabledSettings) { request ->
            requestedUrls += request.url.toString()
            okResponse(request, "mirrored")
        }

        client.newCall(Request.Builder().url(rawUrl).build()).execute().use { response ->
            assertEquals("mirrored", response.body.string())
        }
        assertEquals(listOf("https://ghfast.top/$rawUrl"), requestedUrls)
    }

    @Test
    fun returns304FromMirrorWithoutFallback() {
        val requestedUrls = mutableListOf<String>()
        val client = buildTestClient(enabledSettings) { request ->
            requestedUrls += request.url.toString()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(304)
                .message("Not Modified")
                .body(ByteArray(0).toResponseBody())
                .build()
        }

        client.newCall(Request.Builder().url(rawUrl).build()).execute().use { response ->
            assertEquals(304, response.code)
        }
        assertEquals(listOf("https://ghfast.top/$rawUrl"), requestedUrls)
    }

    @Test
    fun fallsBackToOriginWhenMirrorReturnsHttpFailure() = withSilencedCoreLogs {
        val requestedUrls = mutableListOf<String>()
        val client = buildTestClient(enabledSettings) { request ->
            val url = request.url.toString()
            requestedUrls += url
            if (url.startsWith("https://ghfast.top/")) {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(502)
                    .message("Bad Gateway")
                    .body("gateway error".toResponseBody())
                    .build()
            } else {
                okResponse(request, "origin-fallback")
            }
        }

        client.newCall(Request.Builder().url(rawUrl).build()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("origin-fallback", response.body.string())
        }
        assertEquals(listOf("https://ghfast.top/$rawUrl", rawUrl), requestedUrls)
    }

    @Test
    fun fallsBackToOriginOnIoException() = withSilencedCoreLogs {
        val requestedUrls = mutableListOf<String>()
        val client = buildTestClient(enabledSettings) { request ->
            val url = request.url.toString()
            requestedUrls += url
            if (url.startsWith("https://ghfast.top/")) {
                throw IOException("mirror unreachable")
            }
            okResponse(request, "origin-after-io")
        }

        client.newCall(Request.Builder().url(rawUrl).build()).execute().use { response ->
            assertEquals("origin-after-io", response.body.string())
        }
        assertEquals(listOf("https://ghfast.top/$rawUrl", rawUrl), requestedUrls)
    }

    @Test
    fun doesNotFallBackWhenCallIsCanceled() = withSilencedCoreLogs {
        val requestedUrls = mutableListOf<String>()
        lateinit var call: okhttp3.Call
        val client = buildTestClient(enabledSettings) { request ->
            requestedUrls += request.url.toString()
            call.cancel()
            throw IOException("canceled")
        }
        call = client.newCall(Request.Builder().url(rawUrl).build())

        assertThrows(IOException::class.java) {
            call.execute()
        }
        assertEquals(listOf("https://ghfast.top/$rawUrl"), requestedUrls)
    }

    @Test
    fun skipsNonGetOrAuthenticatedOrNonMirrorableRequests() {
        val requestedUrls = mutableListOf<String>()
        val client = buildTestClient(enabledSettings) { request ->
            requestedUrls += request.url.toString()
            okResponse(request, "direct")
        }

        val postRequest = Request.Builder().url(rawUrl).post("body".toRequestBody()).build()
        val authRequest = Request.Builder().url(rawUrl).header("Authorization", "Bearer token").build()
        val cookieRequest = Request.Builder().url(rawUrl).header("Cookie", "sid=1").build()
        val apiRequest = Request.Builder().url("https://api.github.com/repos/jay3-yy/BiliPai/releases").build()

        listOf(postRequest, authRequest, cookieRequest, apiRequest).forEach { req ->
            client.newCall(req).execute().use { assertEquals("direct", it.body.string()) }
        }
        assertEquals(
            listOf(
                rawUrl,
                rawUrl,
                rawUrl,
                "https://api.github.com/repos/jay3-yy/BiliPai/releases",
            ),
            requestedUrls,
        )
    }

    private fun buildTestClient(
        settings: GithubMirrorSettings,
        terminal: (Request) -> Response,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(GithubMirrorInterceptor { settings })
        .addInterceptor { chain -> terminal(chain.request()) }
        .build()

    private inline fun withSilencedCoreLogs(block: () -> Unit) {
        val previousConfig = CoreNetworkRuntime.config
        CoreNetworkRuntime.config = CoreNetworkConfig(log = { _, _, _, _ -> })
        try {
            block()
        } finally {
            CoreNetworkRuntime.config = previousConfig
        }
    }

    private fun okResponse(request: Request, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody())
        .build()
}
