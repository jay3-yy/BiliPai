package com.android.purebilibili.core.plugin.bpx

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.buildAppDns
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * `.bpx` 隔离脚本运行时（Beta）：
 * 1. 严格使用 CookieJar.NO_COOKIES 独立网络栈，物理级阻断 B 站登录 SESSDATA / bili_jct 泄露；
 * 2. 精确校验 `manifest.permissions.networkDomains` 二级域名白名单；
 * 3. 原生桥接 `BiliPai.http.{get, post, streamSse}`、`BiliPai.crypto`、`BiliPai.storage` 与增量 `emit()`；
 * 4. 复用驻留沙盒并在退入后台时自动释放内存，超时异常联动熔断器。
 */
object BpxSandboxRuntime : BackgroundManager.BackgroundStateListener {
    private const val TAG = "BpxSandbox"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()

    private val isolatedHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(buildAppDns())
            .protocols(NetworkModule.resolveSharedNetworkProtocols())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private var appContext: Context? = null
    private var warmWebView: WebView? = null
    private var listenerRegistered = false
    private val activeRuns = ConcurrentHashMap<String, ActiveRunContext>()

    private data class ActiveRunContext(
        val extensionId: String,
        val allowedDomains: Set<String>,
        val storageDir: File,
        val deferred: CompletableDeferred<String>,
        val onEmit: ((String) -> Unit)? = null,
    )

    @Serializable
    private data class SandboxHttpResponse(
        val status: Int,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
    )

    override fun onEnterBackground() {
        releaseWarmSandbox()
    }

    override fun onEnterForeground() = Unit

    fun releaseWarmSandbox() {
        val wv = warmWebView ?: return
        warmWebView = null
        wv.post {
            runCatching {
                wv.stopLoading()
                wv.removeJavascriptInterface("BpxNativeBridge")
                wv.destroy()
            }
        }
    }

    suspend fun runCompanionSheet(
        context: Context,
        record: InstalledBpxRecord,
        mount: BpxMount,
        videoContextJson: JsonObject,
        onEmit: (BpxSheetDocument) -> Unit,
    ): Result<BpxSheetDocument> = runCatching {
        val script = BpxInstaller.readScriptSource(context, record)
            ?: throw IllegalStateException("找不到扩展源码文件")
        val entryFn = mount.entry?.takeIf { it.isNotBlank() } ?: "onOpenSheet"
        val settingsJson = buildSettingsJson(record)
        val rawResult = executeInSandbox(
            context = context,
            record = record,
            script = script,
            entryFunction = entryFn,
            arg1Json = json.encodeToString(JsonObject.serializer(), videoContextJson),
            arg2Json = json.encodeToString(JsonObject.serializer(), settingsJson),
            timeoutMs = 25_000L,
            onEmit = { partialJson ->
                runCatching { json.decodeFromString(BpxSheetDocument.serializer(), partialJson) }
                    .getOrNull()
                    ?.let(onEmit)
            },
        )
        json.decodeFromString(BpxSheetDocument.serializer(), rawResult)
    }.onFailure { err ->
        Logger.w(TAG, "Companion extension ${record.manifest.id} failed: ${err.message}")
        BpxInstaller.recordCircuitBreakerFailure(context, record.manifest.id)
    }

    suspend fun runCommunityCraft(
        context: Context,
        record: InstalledBpxRecord,
        mount: BpxMount,
        commentsSnapshotJson: JsonObject,
    ): Result<BpxCommentCraftResult> = runCatching {
        val script = BpxInstaller.readScriptSource(context, record)
            ?: throw IllegalStateException("找不到扩展源码文件")
        val entryFn = mount.entry?.takeIf { it.isNotBlank() } ?: "enhanceComments"
        val settingsJson = buildSettingsJson(record)
        val rawResult = executeInSandbox(
            context = context,
            record = record,
            script = script,
            entryFunction = entryFn,
            arg1Json = json.encodeToString(JsonObject.serializer(), commentsSnapshotJson),
            arg2Json = json.encodeToString(JsonObject.serializer(), settingsJson),
            timeoutMs = 2_500L,
            onEmit = null,
        )
        json.decodeFromString(BpxCommentCraftResult.serializer(), rawResult)
    }.onFailure { err ->
        Logger.w(TAG, "Community craft extension ${record.manifest.id} failed: ${err.message}")
        BpxInstaller.recordCircuitBreakerFailure(context, record.manifest.id)
    }

    suspend fun applyHomeLensIfEnabled(
        context: Context,
        videos: List<com.android.purebilibili.data.model.response.VideoItem>,
    ): List<com.android.purebilibili.data.model.response.VideoItem> {
        if (videos.size <= 1) return videos
        BpxInstaller.ensureLoaded(context)
        val lensExtensions = BpxInstaller.extensionsFlow.value.filter { rec ->
            rec.enabled && rec.format == "js" && rec.manifest.mounts.any { it.slot == "home.lens" }
        }
        if (lensExtensions.isEmpty()) return videos

        var current = videos
        for (rec in lensExtensions) {
            val mount = rec.manifest.mounts.firstOrNull { it.slot == "home.lens" } ?: continue
            val reranked = runSingleHomeLens(context, rec, mount, current).getOrNull() ?: continue
            if (reranked.isEmpty()) continue
            val byBvid = current.associateBy { it.bvid }
            val ordered = mutableListOf<com.android.purebilibili.data.model.response.VideoItem>()
            val placed = HashSet<String>()
            for (scored in reranked) {
                val item = byBvid[scored.bvid] ?: continue
                if (!placed.add(scored.bvid)) continue
                val badge = scored.reasonBadge?.trim().orEmpty()
                val decorated = if (badge.isNotEmpty() && !item.title.startsWith("[$badge]")) {
                    item.copy(title = "[$badge] ${item.title}")
                } else item
                ordered.add(decorated)
            }
            for (item in current) {
                if (placed.add(item.bvid)) ordered.add(item)
            }
            current = ordered
        }
        return current
    }

    private suspend fun runSingleHomeLens(
        context: Context,
        record: InstalledBpxRecord,
        mount: BpxMount,
        videos: List<com.android.purebilibili.data.model.response.VideoItem>,
    ): Result<List<BpxLensScoreItem>> = runCatching {
        val script = BpxInstaller.readScriptSource(context, record)
            ?: throw IllegalStateException("找不到扩展源码文件")
        val entryFn = mount.entry?.takeIf { it.isNotBlank() } ?: "rerankCandidates"
        val reqJson = buildJsonObject {
            put(
                "candidates",
                buildJsonArray {
                    videos.take(60).forEach { v ->
                        add(
                            buildJsonObject {
                                put("bvid", JsonPrimitive(v.bvid))
                                put("title", JsonPrimitive(v.title))
                                put("durationSec", JsonPrimitive(v.duration))
                                put("playCount", JsonPrimitive(v.stat.view))
                                put("likeCount", JsonPrimitive(v.stat.like))
                                put("ownerName", JsonPrimitive(v.owner.name))
                            }
                        )
                    }
                }
            )
            put("consumedBvids", buildJsonArray {})
        }
        val settingsJson = buildSettingsJson(record)
        val rawResult = executeInSandbox(
            context = context,
            record = record,
            script = script,
            entryFunction = entryFn,
            arg1Json = json.encodeToString(JsonObject.serializer(), reqJson),
            arg2Json = json.encodeToString(JsonObject.serializer(), settingsJson),
            timeoutMs = 2_500L,
            onEmit = null,
        )
        json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(BpxLensScoreItem.serializer()),
            rawResult,
        )
    }.onFailure { err ->
        Logger.w(TAG, "Home lens extension ${record.manifest.id} failed: ${err.message}")
        BpxInstaller.recordCircuitBreakerFailure(context, record.manifest.id)
    }

    suspend fun runPlayerTracks(
        context: Context,
        bvid: String,
        aid: Long,
        cid: Long,
        title: String,
        durationSec: Long,
    ): List<BpxExternalDanmakuItem> {
        BpxInstaller.ensureLoaded(context)
        val trackExtensions = BpxInstaller.extensionsFlow.value.filter { rec ->
            rec.enabled && rec.format == "js" && rec.manifest.mounts.any { it.slot == "player.track" }
        }
        if (trackExtensions.isEmpty()) return emptyList()

        val ctxJson = buildJsonObject {
            put("bvid", JsonPrimitive(bvid))
            put("aid", JsonPrimitive(aid))
            put("cid", JsonPrimitive(cid))
            put("title", JsonPrimitive(title))
            put("durationSec", JsonPrimitive(durationSec))
        }
        val merged = mutableListOf<BpxExternalDanmakuItem>()
        for (rec in trackExtensions) {
            val mount = rec.manifest.mounts.firstOrNull { it.slot == "player.track" } ?: continue
            runCatching {
                val script = BpxInstaller.readScriptSource(context, rec)
                    ?: throw IllegalStateException("找不到扩展源码文件")
                val entryFn = mount.entry?.takeIf { it.isNotBlank() } ?: "loadTracks"
                val settingsJson = buildSettingsJson(rec)
                val raw = executeInSandbox(
                    context = context,
                    record = rec,
                    script = script,
                    entryFunction = entryFn,
                    arg1Json = json.encodeToString(JsonObject.serializer(), ctxJson),
                    arg2Json = json.encodeToString(JsonObject.serializer(), settingsJson),
                    timeoutMs = 12_000L,
                    onEmit = null,
                )
                json.decodeFromString(BpxPlayerTrackResult.serializer(), raw)
            }.onSuccess { result ->
                merged.addAll(result.danmaku.take(2_500))
            }.onFailure { err ->
                Logger.w(TAG, "Player track extension ${rec.manifest.id} failed: ${err.message}")
                BpxInstaller.recordCircuitBreakerFailure(context, rec.manifest.id)
            }
        }
        return merged
    }

    private fun buildSettingsJson(record: InstalledBpxRecord): JsonObject = buildJsonObject {
        record.settingValues.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureSandboxWebView(ctx: Context): WebView = withContext(Dispatchers.Main) {
        val app = ctx.applicationContext
        appContext = app
        if (!listenerRegistered) {
            listenerRegistered = true
            BackgroundManager.addListener(this@BpxSandboxRuntime)
        }
        warmWebView?.let { return@withContext it }

        val createdReady = CompletableDeferred<Unit>()
        val wv = WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.databaseEnabled = false
            settings.setGeolocationEnabled(false)
            addJavascriptInterface(HostJavascriptBridge(), "BpxNativeBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    createdReady.complete(Unit)
                }
            }
            loadDataWithBaseURL(
                "https://bpx-sandbox.local/",
                "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
                "text/html",
                "UTF-8",
                null,
            )
        }
        createdReady.await()
        warmWebView = wv
        wv
    }

    private suspend fun executeInSandbox(
        context: Context,
        record: InstalledBpxRecord,
        script: String,
        entryFunction: String,
        arg1Json: String,
        arg2Json: String,
        timeoutMs: Long,
        onEmit: ((String) -> Unit)?,
    ): String = mutex.withLock {
        val runId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        val safeId = record.manifest.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val storageDir = File(File(context.applicationContext.filesDir, "bpx_storage"), safeId).also { it.mkdirs() }
        val allowedDomains = record.manifest.permissions.networkDomains.map { it.lowercase().trim() }.toSet()

        activeRuns[runId] = ActiveRunContext(
            extensionId = record.manifest.id,
            allowedDomains = allowedDomains,
            storageDir = storageDir,
            deferred = deferred,
            onEmit = onEmit,
        )
        try {
            withTimeout(timeoutMs) {
                val wv = ensureSandboxWebView(context)
                val harness = buildSandboxHarnessJs(runId, script, entryFunction, arg1Json, arg2Json)
                withContext(Dispatchers.Main) {
                    wv.evaluateJavascript(harness, null)
                }
                deferred.await()
            }
        } finally {
            activeRuns.remove(runId)
        }
    }

    private fun buildSandboxHarnessJs(
        runId: String,
        userScript: String,
        entryFn: String,
        arg1Json: String,
        arg2Json: String,
    ): String {
        val rid = json.encodeToString(runId)
        val fnName = json.encodeToString(entryFn)
        return """
        (async function() {
          const __runId = $rid;
          try {
            globalThis.BPX = undefined;
            const __sseCallbacks = Object.create(null);
            globalThis.__bpxOnSseEvent = function(cbId, dataStr) {
              const fn = __sseCallbacks[cbId];
              if (fn) { try { fn({ data: dataStr }); } catch (_) {} }
            };
            globalThis.BiliPai = {
              http: {
                get: (url, headers) => Promise.resolve(JSON.parse(BpxNativeBridge.httpGet(__runId, String(url), JSON.stringify(headers || {})))),
                post: (url, body, headers) => Promise.resolve(JSON.parse(BpxNativeBridge.httpPost(__runId, String(url), String(body || ''), JSON.stringify(headers || {})))),
                streamSse: (url, init, onEvent) => new Promise((resolve, reject) => {
                  const cbId = 'sse_' + Math.random().toString(36).slice(2);
                  __sseCallbacks[cbId] = onEvent;
                  try {
                    const err = BpxNativeBridge.httpStreamSse(__runId, cbId, String(url), JSON.stringify(init || {}));
                    delete __sseCallbacks[cbId];
                    if (err) reject(new Error(err)); else resolve();
                  } catch (e) {
                    delete __sseCallbacks[cbId];
                    reject(e);
                  }
                })
              },
              crypto: {
                md5: (s) => String(BpxNativeBridge.md5(String(s))),
                sha256: (s) => String(BpxNativeBridge.sha256(String(s))),
                hmacSha256: (k, m) => String(BpxNativeBridge.hmacSha256(String(k), String(m))),
                base64Encode: (s) => String(BpxNativeBridge.base64Encode(String(s))),
                base64Decode: (s) => String(BpxNativeBridge.base64Decode(String(s)))
              },
              storage: {
                get: (k) => Promise.resolve(BpxNativeBridge.storageGet(__runId, String(k))),
                set: (k, v) => { BpxNativeBridge.storageSet(__runId, String(k), String(v)); return Promise.resolve(); },
                remove: (k) => { BpxNativeBridge.storageRemove(__runId, String(k)); return Promise.resolve(); }
              },
              log: (lvl, msg) => BpxNativeBridge.log(__runId, String(lvl), String(msg))
            };
            $userScript
            const pkg = globalThis.BPX;
            const fn = pkg && pkg.handlers && pkg.handlers[$fnName];
            if (typeof fn !== 'function') throw new Error('未找到插槽处理函数: ' + $fnName);
            const emit = (doc) => BpxNativeBridge.emitPartial(__runId, JSON.stringify(doc || {}));
            const out = await fn($arg1Json, $arg2Json, emit);
            BpxNativeBridge.resolveRun(__runId, JSON.stringify(out || {}));
          } catch (err) {
            BpxNativeBridge.rejectRun(__runId, (err && err.message) ? String(err.message) : String(err));
          }
        })();
        """.trimIndent()
    }

    private class HostJavascriptBridge {
        private fun requireDomainAllowed(run: ActiveRunContext, rawUrl: String): URI {
            val uri = URI(rawUrl.trim())
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || scheme == "http") { "仅支持 HTTP(S) 请求: $rawUrl" }
            val host = uri.host?.lowercase()?.trim() ?: throw SecurityException("URL 缺少有效域名")
            if (host !in run.allowedDomains) {
                throw SecurityException("拦截未授权域名请求: $host（未在 permissions.networkDomains 中声明）")
            }
            return uri
        }

        @JavascriptInterface
        fun httpGet(runId: String, url: String, headersJson: String): String {
            val run = activeRuns[runId] ?: throw IllegalStateException("沙盒会话已结束")
            requireDomainAllowed(run, url)
            val req = Request.Builder()
                .url(url)
                .headers(parseHeaders(headersJson))
                .get()
                .build()
            isolatedHttpClient.newCall(req).execute().use { resp ->
                return json.encodeToString(
                    SandboxHttpResponse(
                        status = resp.code,
                        body = resp.body.string(),
                        headers = resp.headers.toMap(),
                    )
                )
            }
        }

        @JavascriptInterface
        fun httpPost(runId: String, url: String, body: String, headersJson: String): String {
            val run = activeRuns[runId] ?: throw IllegalStateException("沙盒会话已结束")
            requireDomainAllowed(run, url)
            val headers = parseHeaders(headersJson)
            val contentType = headers["Content-Type"] ?: "application/json; charset=utf-8"
            val req = Request.Builder()
                .url(url)
                .headers(headers)
                .post(body.toRequestBody(contentType.toMediaTypeOrNull()))
                .build()
            isolatedHttpClient.newCall(req).execute().use { resp ->
                return json.encodeToString(
                    SandboxHttpResponse(
                        status = resp.code,
                        body = resp.body.string(),
                        headers = resp.headers.toMap(),
                    )
                )
            }
        }

        @JavascriptInterface
        fun httpStreamSse(runId: String, cbId: String, url: String, initJson: String): String {
            val run = activeRuns[runId] ?: return "沙盒会话已结束"
            return try {
                requireDomainAllowed(run, url)
                val initObj = json.parseToJsonElement(initJson) as? JsonObject ?: JsonObject(emptyMap())
                val method = (initObj["method"] as? JsonPrimitive)?.content?.uppercase() ?: "POST"
                val bodyStr = (initObj["body"] as? JsonPrimitive)?.content.orEmpty()
                val headersObj = initObj["headers"] as? JsonObject
                val headersBuilder = okhttp3.Headers.Builder()
                headersObj?.forEach { (k, v) ->
                    (v as? JsonPrimitive)?.content?.let { headersBuilder.add(k, it) }
                }
                val headers = headersBuilder.build()
                val reqBuilder = Request.Builder().url(url).headers(headers)
                if (method == "GET") {
                    reqBuilder.get()
                } else {
                    val ct = headers["Content-Type"] ?: "application/json; charset=utf-8"
                    reqBuilder.post(bodyStr.toRequestBody(ct.toMediaTypeOrNull()))
                }
                isolatedHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return "HTTP ${resp.code}: ${resp.body.string().take(180)}"
                    }
                    val source = resp.body.source()
                    while (!source.exhausted() && activeRuns.containsKey(runId)) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val dataPayload = line.removePrefix("data:").trim()
                            if (dataPayload.isNotEmpty()) {
                                val wv = warmWebView
                                val jsCall = "globalThis.__bpxOnSseEvent && globalThis.__bpxOnSseEvent(${json.encodeToString(cbId)}, ${json.encodeToString(dataPayload)});"
                                wv?.post { wv.evaluateJavascript(jsCall, null) }
                            }
                        }
                    }
                }
                ""
            } catch (e: Exception) {
                e.message ?: "SSE 请求失败"
            }
        }

        @JavascriptInterface
        fun emitPartial(runId: String, docJson: String) {
            activeRuns[runId]?.onEmit?.invoke(docJson)
        }

        @JavascriptInterface
        fun resolveRun(runId: String, payloadJson: String) {
            activeRuns[runId]?.deferred?.complete(payloadJson)
        }

        @JavascriptInterface
        fun rejectRun(runId: String, message: String) {
            activeRuns[runId]?.deferred?.completeExceptionally(
                IllegalStateException(message.ifBlank { "扩展脚本执行异常" })
            )
        }

        @JavascriptInterface
        fun storageGet(runId: String, key: String): String? {
            val dir = activeRuns[runId]?.storageDir ?: return null
            val f = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_"))
            return if (f.isFile) runCatching { f.readText() }.getOrNull() else null
        }

        @JavascriptInterface
        fun storageSet(runId: String, key: String, value: String) {
            val dir = activeRuns[runId]?.storageDir ?: return
            if (value.length > 65536) return
            val f = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_"))
            runCatching { f.writeText(value) }
        }

        @JavascriptInterface
        fun storageRemove(runId: String, key: String) {
            val dir = activeRuns[runId]?.storageDir ?: return
            File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_")).delete()
        }

        @JavascriptInterface
        fun md5(input: String): String = digestHex("MD5", input)

        @JavascriptInterface
        fun sha256(input: String): String = digestHex("SHA-256", input)

        @JavascriptInterface
        fun hmacSha256(key: String, message: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        @JavascriptInterface
        fun base64Encode(input: String): String =
            Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        @JavascriptInterface
        fun base64Decode(b64: String): String =
            Base64.decode(b64, Base64.DEFAULT).toString(Charsets.UTF_8)

        @JavascriptInterface
        fun log(runId: String, level: String, message: String) {
            val extId = activeRuns[runId]?.extensionId ?: "unknown"
            Logger.d(TAG, "[$extId][$level] $message")
        }

        private fun digestHex(algorithm: String, text: String): String =
            MessageDigest.getInstance(algorithm)
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun parseHeaders(headersJson: String): okhttp3.Headers {
            val obj = runCatching { json.parseToJsonElement(headersJson) as? JsonObject }.getOrNull()
            val builder = okhttp3.Headers.Builder()
            obj?.forEach { (k, v) ->
                (v as? JsonPrimitive)?.content?.takeIf { k.isNotBlank() }?.let { builder.add(k, it) }
            }
            if (builder["User-Agent"] == null) {
                builder.add("User-Agent", "BiliPai-BPX-Sandbox/1.0")
            }
            return builder.build()
        }
    }
}
