@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.player

import android.content.Context
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.android.purebilibili.core.network.CoreNetworkRuntime
import com.android.purebilibili.core.network.policy.shouldEnableTrustAllCertificates
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient

internal const val HTTP_ENGINE_MIN_SDK_INT = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
internal const val HTTP_ENGINE_MIN_S_EXTENSION_VERSION = 7
private const val PLAYBACK_CONNECT_TIMEOUT_MS = 15_000
private const val PLAYBACK_READ_TIMEOUT_MS = 20_000
private val PROXY_PROBE_URI: URI = URI.create("https://upos-sz-mirrorcos.bilivideo.com/")

/**
 * 判断是否允许优先启用系统内置 `HttpEngine`（QUIC / HTTP/3）：
 * - 需 Android 14+（S-Extension >= 7）；
 * - 若启用自定义应用代理、存在系统 HTTP 代理（防本机回环代理触发 ECONNREFUSED）
 *   或调试期信任全部证书（抓包场景），自动保留 OkHttp 通道。
 */
internal fun shouldUsePlatformHttpEngine(
    sdkInt: Int,
    sExtensionVersion: Int,
    customProxyEnabled: Boolean,
    trustAllCertificates: Boolean,
    systemHttpProxyActive: Boolean = false,
): Boolean {
    if (sdkInt < HTTP_ENGINE_MIN_SDK_INT) return false
    if (sExtensionVersion < HTTP_ENGINE_MIN_S_EXTENSION_VERSION) return false
    if (customProxyEnabled || systemHttpProxyActive || trustAllCertificates) return false
    return true
}

internal fun splitHttpEngineHeaders(headers: Map<String, String>): Pair<String?, Map<String, String>> {
    if (headers.isEmpty()) return null to emptyMap()
    var userAgent: String? = null
    val remaining = LinkedHashMap<String, String>(headers.size)
    headers.forEach { (key, value) ->
        if (key.equals("User-Agent", ignoreCase = true)) {
            userAgent = value
        } else {
            remaining[key] = value
        }
    }
    return userAgent to remaining
}

private fun hasSystemProxyProperty(): Boolean {
    val propHost = System.getProperty("http.proxyHost") ?: System.getProperty("https.proxyHost")
    return !propHost.isNullOrBlank()
}

private fun probeProxySelectorOffMain(): Boolean {
    val proxies = runCatching { ProxySelector.getDefault()?.select(PROXY_PROBE_URI).orEmpty() }
        .getOrDefault(emptyList())
    return proxies.any { it.type() != Proxy.Type.DIRECT }
}

/**
 * 为媒体播放构造上游 HTTP `DataSource.Factory`：
 * - Android 14+ 优先走系统内置 `HttpEngine`（支持 HTTP/3 QUIC、0-RTT 与无队头阻塞，零 APK 体积增量）；
 * - `HttpEngine` 构建与 `ProxySelector` 探测严格在后台预热线程执行，调用方（含主线程）绝不阻塞；
 *   若尚未预热完成则本次即时回退 `OkHttpDataSource.Factory` 并异步触发预热，后续播放无缝命中 `HttpEngine`；
 * - 禁用 `HttpEngine` 自带磁盘缓存，避免与 Media3 `SimpleCache` 双重落盘；
 * - 旧系统、无系统主线模块、存在代理或初始化异常时自动回退至 `OkHttpDataSource.Factory`。
 */
object PlaybackHttpDataSourceFactory {
    private val engineLock = Any()

    @Volatile
    private var sharedHttpEngine: Any? = null

    @Volatile
    private var engineInitFailed = false

    @Volatile
    private var systemProxyDetected = false

    private val warmUpScheduled = AtomicBoolean(false)

    private val threadCounter = AtomicInteger(1)
    private val sharedExecutor: Executor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "BiliPai-HttpEngine-${threadCounter.getAndIncrement()}").apply {
                isDaemon = true
            }
        }
    }

    /**
     * 在后台线程异步探测系统代理并预热 `HttpEngine`，避免在主线程首次点击视频时触发冷启动建链耗时。
     */
    fun warmUpAsync(context: Context?) {
        val appContext = context?.applicationContext ?: return
        if (engineInitFailed || sharedHttpEngine != null) return
        val sExtVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)
        } else {
            0
        }
        val trustAll = shouldEnableTrustAllCertificates(CoreNetworkRuntime.config.debug)
        if (!shouldUsePlatformHttpEngine(
                sdkInt = Build.VERSION.SDK_INT,
                sExtensionVersion = sExtVersion,
                customProxyEnabled = false,
                trustAllCertificates = trustAll,
                systemHttpProxyActive = hasSystemProxyProperty(),
            )
        ) {
            return
        }
        if (!warmUpScheduled.compareAndSet(false, true)) return
        sharedExecutor.execute {
            val proxyActive = hasSystemProxyProperty() || probeProxySelectorOffMain()
            systemProxyDetected = proxyActive
            if (!proxyActive &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= HTTP_ENGINE_MIN_S_EXTENSION_VERSION
            ) {
                getOrCreateHttpEngineApi34(appContext)
            }
        }
    }

    fun create(
        context: Context?,
        fallbackClient: OkHttpClient,
        headers: Map<String, String> = emptyMap(),
        customProxyEnabled: Boolean = false,
    ): DataSource.Factory {
        val appContext = context?.applicationContext
        val sExtVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)
        } else {
            0
        }
        val trustAll = shouldEnableTrustAllCertificates(CoreNetworkRuntime.config.debug)
        val systemProxyActive = hasSystemProxyProperty() || systemProxyDetected
        if (appContext != null &&
            !engineInitFailed &&
            shouldUsePlatformHttpEngine(
                sdkInt = Build.VERSION.SDK_INT,
                sExtensionVersion = sExtVersion,
                customProxyEnabled = customProxyEnabled,
                trustAllCertificates = trustAll,
                systemHttpProxyActive = systemProxyActive,
            ) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= HTTP_ENGINE_MIN_S_EXTENSION_VERSION
        ) {
            createHttpEngineFactoryIfReadyApi34(headers)?.let { return it }
            warmUpAsync(appContext)
        }
        return OkHttpDataSource.Factory(fallbackClient).apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_MIN_S_EXTENSION_VERSION)
    private fun createHttpEngineFactoryIfReadyApi34(
        headers: Map<String, String>,
    ): DataSource.Factory? {
        val engine = sharedHttpEngine as? HttpEngine ?: return null
        val (userAgent, requestHeaders) = splitHttpEngineHeaders(headers)
        return HttpEngineDataSource.Factory(engine, sharedExecutor)
            .setConnectionTimeoutMs(PLAYBACK_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(PLAYBACK_READ_TIMEOUT_MS)
            .setResetTimeoutOnRedirects(true)
            .apply {
                if (!userAgent.isNullOrBlank()) setUserAgent(userAgent)
                if (requestHeaders.isNotEmpty()) setDefaultRequestProperties(requestHeaders)
            }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_MIN_S_EXTENSION_VERSION)
    private fun getOrCreateHttpEngineApi34(appContext: Context): HttpEngine? {
        (sharedHttpEngine as? HttpEngine)?.let { return it }
        if (engineInitFailed) return null
        synchronized(engineLock) {
            (sharedHttpEngine as? HttpEngine)?.let { return it }
            if (engineInitFailed) return null
            val created = try {
                HttpEngine.Builder(appContext)
                    .setEnableHttp2(true)
                    .setEnableQuic(true)
                    .setEnableBrotli(true)
                    .setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISABLED, 0L)
                    .build()
            } catch (_: Throwable) {
                engineInitFailed = true
                null
            }
            if (created != null) {
                sharedHttpEngine = created
            }
            return created
        }
    }
}
