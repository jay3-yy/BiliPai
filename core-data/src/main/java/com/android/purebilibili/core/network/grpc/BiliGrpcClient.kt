package com.android.purebilibili.core.network.grpc

import com.android.purebilibili.core.network.CoreDataLog
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.UUID

enum class BiliGrpcProfile(
    internal val deviceMobiApp: String,
    internal val includeAccount: Boolean,
) {
    ANDROID_HD("android_hd", true),
    // 公开动态摘要由 Device.mobi_app 选择主端结构，不携带或切换用户登录凭据。
    DYNAMIC_PREVIEW_GUEST("android", false),
}

/** Keep transport status and numeric Bilibili business errors separate from display text. */
class BiliGrpcException(
    val httpCode: Int,
    val grpcStatus: String?,
    val grpcMessage: String?,
) : IllegalStateException("请求失败：HTTP $httpCode，gRPC $grpcStatus，${grpcMessage.orEmpty()}") {
    val businessCode: Int? =
        if (httpCode in 200..299 && grpcStatus == "2") grpcMessage?.toIntOrNull() else null
}

// gRPC 直连客户端为共享网络能力，手机与 TV 的评论、私信分享均通过它调用。
object BiliGrpcClient {
    private const val APP_BASE_URL = "https://app.bilibili.com"
    private const val USER_AGENT =
        "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2"
    private const val TRACE_ID = "11111111111111111111111111111111:1111111111111111:0:0"
    private val grpcMediaType = "application/grpc".toMediaType()
    private val dynamicPreviewClient by lazy {
        NetworkModule.guestOkHttpClient.newBuilder()
            // App gRPC 不能经过游客 Web 的 UA/Origin/Referer 或 CookieJar 改写。
            .cookieJar(CookieJar.NO_COOKIES)
            .apply { interceptors().clear() }
            .build()
    }

    fun request(
        path: String,
        message: ByteArray,
        profile: BiliGrpcProfile = BiliGrpcProfile.ANDROID_HD,
    ): ByteArray {
        val url = "$APP_BASE_URL$path"
        val buvid = resolveBuvid()
        val request = Request.Builder()
            .url(url)
            .post(ProtoWire.frame(message).toRequestBody(grpcMediaType))
            .header("content-type", "application/grpc")
            .header("grpc-encoding", "gzip")
            .header("gzip-accept-encoding", "gzip,identity")
            .header("user-agent", USER_AGENT)
            .header("bili-http-engine", "cronet")
            .header("x-bili-trace-id", TRACE_ID)
            .header("buvid", buvid)
            .header("cookie", if (profile.includeAccount) buildCookieHeader() else "buvid3=$buvid")
            .header("x-bili-metadata-bin", buildMetadataHeader(buvid, profile))
            .header("x-bili-device-bin", buildDeviceHeader(buvid, profile))
            .header("x-bili-fawkes-req-bin", buildFawkesHeader())
            .header("x-bili-network-bin", buildNetworkHeader())
            .header("x-bili-locale-bin", buildLocaleHeader())
            .apply {
                if (profile.includeAccount) {
                    TokenManager.accessTokenCache
                        ?.takeIf { it.isNotBlank() }
                        ?.let { header("authorization", "identify_v1 $it") }
                }
            }
            .build()

        val client = if (profile.includeAccount) NetworkModule.okHttpClient else dynamicPreviewClient
        client.newCall(request).execute().use { response ->
            val body = response.body.bytes()
            val trailers = runCatching { response.trailers() }.getOrNull()
            val grpcStatus = response.header("grpc-status")
                ?: trailers?.get("grpc-status")
            val grpcMessage = response.header("grpc-message")
                ?: trailers?.get("grpc-message")

            // HTTP 200 with standard gRPC status (0 or absent on HTTP success) is considered valid
            val isStatusOk = grpcStatus == null || grpcStatus == "0"
            if (!response.isSuccessful || !isStatusOk) {
                CoreDataLog.w(
                    "BiliGrpc",
                    "gRPC request failed: path=$path http=${response.code} grpc=$grpcStatus message=${grpcMessage.orEmpty()}"
                )
                throw BiliGrpcException(response.code, grpcStatus, grpcMessage)
            }
            return ProtoWire.unframe(body)
        }
    }

    internal fun buildMetadataHeader(
        buvid: String = resolveBuvid(),
        profile: BiliGrpcProfile = BiliGrpcProfile.ANDROID_HD,
    ): String {
        val accessKey = TokenManager.accessTokenCache?.takeIf { profile.includeAccount && it.isNotBlank() }
        val fields = mutableListOf<ByteArray>()
        if (!accessKey.isNullOrBlank()) {
            fields += ProtoWire.string(1, accessKey)
        }
        fields += ProtoWire.string(2, "android_hd")
        fields += ProtoWire.string(3, "android")
        fields += ProtoWire.int32(4, 2001100)
        fields += ProtoWire.string(5, "master")
        fields += ProtoWire.string(6, buvid)
        fields += ProtoWire.string(7, "android")
        return encodeBase64(ProtoWire.message(*fields.toTypedArray()))
    }

    internal fun buildDeviceHeader(
        buvid: String = resolveBuvid(),
        profile: BiliGrpcProfile = BiliGrpcProfile.ANDROID_HD,
    ): String {
        val fields = arrayOf(
            ProtoWire.int32(1, 5),
            ProtoWire.int32(2, 2001100),
            ProtoWire.string(3, buvid),
            ProtoWire.string(4, profile.deviceMobiApp),
            ProtoWire.string(5, "android"),
            ProtoWire.string(6, "android"),
            ProtoWire.string(7, "master"),
            ProtoWire.string(8, "android"),
            ProtoWire.string(9, "android"),
            ProtoWire.string(10, "15"),
            ProtoWire.string(13, "2.0.1")
        )
        return encodeBase64(ProtoWire.message(*fields))
    }

    internal fun buildFawkesHeader(): String {
        val sessionId = UUID.randomUUID().toString().replace("-", "").take(8)
        val fields = arrayOf(
            ProtoWire.string(1, "android_hd"),
            ProtoWire.string(2, "prod"),
            ProtoWire.string(3, sessionId)
        )
        return encodeBase64(ProtoWire.message(*fields))
    }

    internal fun buildNetworkHeader(): String {
        return encodeBase64(ProtoWire.message(ProtoWire.int32(1, 1)))
    }

    internal fun buildLocaleHeader(): String {
        val localeIds = ProtoWire.message(
            ProtoWire.string(1, "zh"),
            ProtoWire.string(2, "Hans"),
            ProtoWire.string(3, "CN")
        )
        val locale = ProtoWire.message(
            ProtoWire.bytes(1, localeIds),
            ProtoWire.bytes(2, localeIds),
            ProtoWire.string(3, "Asia/Shanghai")
        )
        return encodeBase64(locale)
    }

    internal fun buildCookieHeader(): String {
        val cookies = mutableListOf("buvid3=${resolveBuvid()}")
        TokenManager.sessDataCache?.takeIf { it.isNotBlank() }?.let {
            cookies += "SESSDATA=$it"
        }
        TokenManager.csrfCache?.takeIf { it.isNotBlank() }?.let {
            cookies += "bili_jct=$it"
        }
        TokenManager.midCache?.takeIf { it > 0L }?.let {
            cookies += "DedeUserID=$it"
        }
        return cookies.joinToString("; ")
    }

    internal fun resolveBuvid(): String {
        val cached = TokenManager.buvid3Cache
        if (!cached.isNullOrBlank()) return cached
        val generated = UUID.randomUUID().toString().replace("-", "") + "infoc"
        TokenManager.buvid3Cache = generated
        return generated
    }

    internal fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }
}
