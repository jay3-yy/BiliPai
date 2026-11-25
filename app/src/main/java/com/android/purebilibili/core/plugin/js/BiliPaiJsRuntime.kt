package com.android.purebilibili.core.plugin.js

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.plugin.sdk.PluginCapability
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BiliPaiJsRuntime(
    context: Context,
    private val storageRoot: File = File(context.filesDir, "bilipai_js_plugin_storage"),
    private val timeoutMillis: Long = 15_000L,
    private val moduleCache: BiliPaiJsModuleResultCache = BiliPaiJsModuleResultCache.createDefault(context)
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun previewManifest(script: String): Result<BiliPaiJsPluginManifest> = runCatching {
        val payload = runScript(
            pluginId = "preview",
            script = script,
            expression = buildBiliPaiJsPreviewExpression()
        )
        val manifest = json.decodeFromString(BiliPaiJsPluginManifest.serializer(), payload)
        validateBiliPaiJsPluginManifest(manifest)?.let { error ->
            throw IllegalArgumentException(error)
        }
        manifest
    }

    suspend fun loadModuleItems(
        installed: InstalledBiliPaiJsPlugin,
        module: BiliPaiJsModule,
        paramsJson: String = "{}"
    ): Result<List<BiliPaiJsMediaItem>> = runCatching {
        val normalizedParams = paramsJson.ifBlank { "{}" }
        val moduleId = module.id.ifBlank { module.functionName }
        moduleCache.read(
            pluginId = installed.manifest.id,
            moduleId = moduleId,
            paramsJson = normalizedParams,
            cacheDurationSeconds = module.cacheDuration
        )?.let { cachedPayload ->
            return@runCatching decodeMediaItems(cachedPayload)
        }
        val script = File(installed.scriptPath).readText(Charsets.UTF_8)
        val expression = buildBiliPaiJsModuleExpression(
            functionName = module.functionName,
            paramsJson = normalizedParams
        )
        val payload = runScript(
            pluginId = installed.manifest.id,
            script = script,
            expression = expression
        )
        moduleCache.write(
            pluginId = installed.manifest.id,
            moduleId = moduleId,
            paramsJson = normalizedParams,
            cacheDurationSeconds = module.cacheDuration,
            payload = payload
        )
        decodeMediaItems(payload)
    }

    fun clearPluginCache(pluginId: String) {
        moduleCache.clearPlugin(pluginId)
    }

    /**
     * 调用插件的详情函数（manifest.detailFunctionName），参数为 `{"link": ...}`，
     * 结果按 manifest.detailCacheDuration 缓存（moduleId 固定为 `__detail__`）。
     */
    suspend fun loadDetailItems(
        installed: InstalledBiliPaiJsPlugin,
        link: String
    ): Result<List<BiliPaiJsMediaItem>> = runCatching {
        val functionName = installed.manifest.detailFunctionName
        if (functionName.isBlank()) {
            throw IllegalArgumentException("插件未声明详情函数（detailFunctionName）")
        }
        val cacheKey = """{"link":${Json.encodeToString(link)}}"""
        moduleCache.read(
            pluginId = installed.manifest.id,
            moduleId = DETAIL_CACHE_MODULE_ID,
            paramsJson = cacheKey,
            cacheDurationSeconds = installed.manifest.detailCacheDuration
        )?.let { cachedPayload ->
            return@runCatching decodeMediaItems(cachedPayload)
        }
        val script = File(installed.scriptPath).readText(Charsets.UTF_8)
        val expression = buildBiliPaiJsModuleExpression(
            functionName = functionName,
            paramsJson = cacheKey
        )
        val payload = runScript(
            pluginId = installed.manifest.id,
            script = script,
            expression = expression
        )
        moduleCache.write(
            pluginId = installed.manifest.id,
            moduleId = DETAIL_CACHE_MODULE_ID,
            paramsJson = cacheKey,
            cacheDurationSeconds = installed.manifest.detailCacheDuration,
            payload = payload
        )
        decodeMediaItems(payload)
    }

    private fun decodeMediaItems(payload: String): List<BiliPaiJsMediaItem> {
        return json.decodeFromString(ListSerializer(BiliPaiJsMediaItem.serializer()), payload)
    }

    /**
     * 调用插件的弹幕函数（manifest.danmakuFunctionName），参数为 `{"title": ...}`。
     * 仅在插件被授予 [com.android.purebilibili.plugin.sdk.PluginCapability.DANMAKU_STREAM]
     * 时可用；结果按 manifest.danmakuCacheDuration 缓存（moduleId 固定为 `__danmaku__`）。
     */
    suspend fun loadDanmuComments(
        installed: InstalledBiliPaiJsPlugin,
        title: String
    ): Result<List<BiliPaiJsDanmuComment>> = runCatching {
        val functionName = installed.manifest.danmakuFunctionName
        if (functionName.isBlank()) {
            throw IllegalArgumentException("插件未声明弹幕函数（danmakuFunctionName）")
        }
        if (PluginCapability.DANMAKU_STREAM !in installed.grantedCapabilities) {
            throw SecurityException("插件未获得 DANMAKU_STREAM 弹幕流权限")
        }
        val cacheKey = """{"title":${Json.encodeToString(title)}}"""
        moduleCache.read(
            pluginId = installed.manifest.id,
            moduleId = DANMAKU_CACHE_MODULE_ID,
            paramsJson = cacheKey,
            cacheDurationSeconds = installed.manifest.danmakuCacheDuration
        )?.let { cachedPayload ->
            return@runCatching decodeDanmuComments(cachedPayload)
        }
        val script = File(installed.scriptPath).readText(Charsets.UTF_8)
        val expression = buildBiliPaiJsModuleExpression(
            functionName = functionName,
            paramsJson = cacheKey
        )
        val payload = runScript(
            pluginId = installed.manifest.id,
            script = script,
            expression = expression
        )
        moduleCache.write(
            pluginId = installed.manifest.id,
            moduleId = DANMAKU_CACHE_MODULE_ID,
            paramsJson = cacheKey,
            cacheDurationSeconds = installed.manifest.danmakuCacheDuration,
            payload = payload
        )
        decodeDanmuComments(payload)
    }

    private fun decodeDanmuComments(payload: String): List<BiliPaiJsDanmuComment> {
        return json.decodeFromString(ListSerializer(BiliPaiJsDanmuComment.serializer()), payload)
    }

    private companion object {
        const val DETAIL_CACHE_MODULE_ID = "__detail__"
        const val DANMAKU_CACHE_MODULE_ID = "__danmaku__"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runScript(
        pluginId: String,
        script: String,
        expression: String
    ): String = try {
        withTimeout(timeoutMillis) {
            val storageBridge = withContext(Dispatchers.IO) {
                StorageBridge.load(File(storageRoot, pluginId))
            }
            withContext(Dispatchers.Main.immediate) {
                val callId = UUID.randomUUID().toString()
                val result = CompletableDeferred<String>()
                val webView = WebView(appContext)
                val callbacks = CallbackBridge(result)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = false
                webView.settings.allowFileAccess = false
                webView.settings.allowContentAccess = false
                webView.settings.databaseEnabled = false
                webView.settings.setGeolocationEnabled(false)
                webView.addJavascriptInterface(callbacks, "BiliPaiNative")
                webView.addJavascriptInterface(HttpBridge(), "BiliPaiHttpNative")
                webView.addJavascriptInterface(storageBridge, "BiliPaiStorageNative")
                webView.addJavascriptInterface(LogBridge(pluginId), "BiliPaiLogNative")
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(buildExecutionScript(callId, script, expression), null)
                    }
                }
                webView.loadDataWithBaseURL(
                    "https://plugins.bilipai.local/",
                    "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
                    "text/html",
                    "UTF-8",
                    null
                )
                try {
                    result.await()
                } finally {
                    webView.stopLoading()
                    webView.clearCache(false)
                    webView.removeJavascriptInterface("BiliPaiNative")
                    webView.removeJavascriptInterface("BiliPaiHttpNative")
                    webView.removeJavascriptInterface("BiliPaiStorageNative")
                    webView.removeJavascriptInterface("BiliPaiLogNative")
                    webView.destroy()
                }
            }
        }
    } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
        // 手势层面整次调用作废，转换成用户可读的失败原因。
        @Suppress("NAME_SHADOWING")
        throw IllegalStateException(
            "执行超时（${timeoutMillis / 1000} 秒）：数据源未在时限内返回，请检查网络或数据源地址是否可达"
        )
    }

    private fun buildExecutionScript(
        callId: String,
        script: String,
        expression: String
    ): String = buildBiliPaiJsExecutionScript(callId, script, expression)

    private inner class CallbackBridge(
        private val result: CompletableDeferred<String>
    ) {
        @JavascriptInterface
        fun resolve(callId: String, payload: String) {
            result.complete(payload)
        }

        @JavascriptInterface
        fun reject(callId: String, message: String) {
            result.completeExceptionally(IllegalStateException(message.ifBlank { "JS 插件执行失败" }))
        }
    }

    private inner class HttpBridge {
        @JavascriptInterface
        fun get(url: String, headersJson: String): String {
            val request = Request.Builder()
                .url(url)
                .headers(parseHeaders(headersJson))
                .apply { defaultUserAgentIfMissing(this) }
                .get()
                .build()
            return executeRequest(request)
        }

        @JavascriptInterface
        fun post(url: String, body: String, headersJson: String): String {
            val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .headers(parseHeaders(headersJson))
                .apply { defaultUserAgentIfMissing(this) }
                .post(requestBody)
                .build()
            return executeRequest(request)
        }

        private fun defaultUserAgentIfMissing(builder: Request.Builder) {
            if (builder.build().header("User-Agent") == null) {
                builder.header("User-Agent", "BiliPai JS Plugin")
            }
        }

        private fun executeRequest(request: Request): String {
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                return json.encodeToString(
                    HttpResponse.serializer(),
                    HttpResponse(
                        code = response.code,
                        body = response.body.string(),
                        headers = response.headers.toMap()
                    )
                )
            }
        }

        private fun parseHeaders(headersJson: String): okhttp3.Headers {
            val map = runCatching {
                json.decodeFromString<Map<String, String>>(headersJson)
            }.getOrDefault(emptyMap())
            return okhttp3.Headers.Builder().apply {
                map.forEach { (name, value) ->
                    if (name.isNotBlank()) add(name, value)
                }
            }.build()
        }
    }

    private class StorageBridge private constructor(
        private val storageDir: File,
        private val values: ConcurrentHashMap<String, String>,
    ) {
        companion object {
            fun load(storageDir: File): StorageBridge {
                val loaded = ConcurrentHashMap<String, String>()
                storageDir.mkdirs()
                storageDir.listFiles { file -> file.isFile }?.forEach { file ->
                    loaded[file.name] = file.readText(Charsets.UTF_8)
                }
                return StorageBridge(storageDir, loaded)
            }
        }

        @JavascriptInterface
        fun get(key: String): String? = values[key]

        @JavascriptInterface
        fun set(key: String, value: String) {
            if (key.isBlank()) return
            values[key] = value
            File(storageDir, key.safeStorageName()).writeText(value, Charsets.UTF_8)
        }

        @JavascriptInterface
        fun remove(key: String) {
            values.remove(key)
            File(storageDir, key.safeStorageName()).delete()
        }
    }

    private class LogBridge(
        private val pluginId: String
    ) {
        @JavascriptInterface
        fun write(message: String) {
            Log.d("BiliPaiJsPlugin", "[$pluginId] $message")
        }
    }

    @kotlinx.serialization.Serializable
    private data class HttpResponse(
        val code: Int,
        val body: String,
        val headers: Map<String, String>
    )
}

private fun String.safeStorageName(): String {
    return replace(Regex("[^A-Za-z0-9_.-]"), "_").take(96)
}

internal fun buildBiliPaiJsPreviewExpression(): String {
    return """
        const plugin = window.BiliPaiPlugin || globalThis.BiliPaiPlugin;
        if (!plugin) {
          throw new Error('未找到 BiliPaiPlugin，请使用 BiliPai 原生 JS 插件格式');
        }
        return plugin;
    """.trimIndent()
}

internal fun buildBiliPaiJsModuleExpression(
    functionName: String,
    paramsJson: String
): String {
    return """
        const plugin = window.BiliPaiPlugin || globalThis.BiliPaiPlugin || {};
        const functionName = ${Json.encodeToString(functionName)};
        const fn = plugin[functionName] || window[functionName] || globalThis[functionName];
        if (typeof fn !== 'function') {
          throw new Error('未找到模块函数: ' + functionName);
        }
        return fn(${paramsJson.ifBlank { "{}" }});
    """.trimIndent()
}

internal fun buildBiliPaiJsExecutionScript(
    callId: String,
    pluginScript: String,
    expression: String
): String {
    return """
        (function() {
          const callId = ${Json.encodeToString(callId)};
          function biliPaiWrapDomNode(node) {
            if (!node) return null;
            return {
              node: node,
              get tagName() { return node.tagName ? String(node.tagName).toLowerCase() : ''; },
              get text() { return String(node.textContent == null ? '' : node.textContent).trim(); },
              get html() { return String(node.innerHTML == null ? '' : node.innerHTML); },
              attr: function(name) { return node.getAttribute ? node.getAttribute(String(name)) : null; },
              select: function(selector) { return biliPaiWrapDomAll(node.querySelectorAll(String(selector))); },
              selectOne: function(selector) { return biliPaiWrapDomNode(node.querySelector(String(selector))); }
            };
          }
          function biliPaiWrapDomAll(nodeList) {
            var wrapped = [];
            for (var i = 0; i < nodeList.length; i++) {
              wrapped.push(biliPaiWrapDomNode(nodeList[i]));
            }
            return wrapped;
          }
          window.BiliPai = {
            http: {
              get: function(url, headers) {
                return JSON.parse(BiliPaiHttpNative.get(String(url), JSON.stringify(headers || {})));
              },
              post: function(url, body, headers) {
                return JSON.parse(BiliPaiHttpNative.post(String(url), String(body || ''), JSON.stringify(headers || {})));
              }
            },
            dom: {
              parse: function(html) {
                var parsed = new DOMParser().parseFromString(String(html == null ? '' : html), 'text/html');
                var root = biliPaiWrapDomNode(parsed);
                root.title = String(parsed.title == null ? '' : parsed.title);
                root.body = biliPaiWrapDomNode(parsed.body);
                return root;
              }
            },
            storage: {
              get: function(key) { return BiliPaiStorageNative.get(String(key)); },
              set: function(key, value) { BiliPaiStorageNative.set(String(key), String(value)); },
              remove: function(key) { BiliPaiStorageNative.remove(String(key)); }
            },
            log: function(message) { BiliPaiLogNative.write(String(message)); }
          };
          globalThis.BiliPai = window.BiliPai;
          const finish = function(value) {
            BiliPaiNative.resolve(callId, JSON.stringify(value == null ? null : value));
          };
          const fail = function(error) {
            const message = error && error.message ? error.message : String(error);
            BiliPaiNative.reject(callId, message);
          };
          try {
            $pluginScript
            const value = (function() {
              $expression
            })();
            Promise.resolve(value).then(finish).catch(fail);
          } catch (error) {
            fail(error);
          }
        })();
    """.trimIndent()
}
