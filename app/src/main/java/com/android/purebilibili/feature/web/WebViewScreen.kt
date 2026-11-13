package com.android.purebilibili.feature.web

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress

import com.android.purebilibili.core.ui.components.AppIcon

import android.view.ViewGroup
import android.content.Context
import android.webkit.RenderProcessGoneDetail
import android.widget.Toast
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.hasCommentLocation

/**
 * WebViewScreen - 应用内浏览器
 *
 * 支持拦截 Bilibili 链接并跳转到原生界面：
 * - 视频: bilibili.com/video/BV... 或 av...
 * - UP主空间: space.bilibili.com/{mid}
 * - 直播: live.bilibili.com/{roomId}
 * - 动态: t.bilibili.com/{dynamicId} 或 opus/{dynamicId}
 * - 番剧: bilibili.com/bangumi/play/ss{id} 或 ep{id}
 * - 音乐: music.bilibili.com/h5/music-detail?music_id=...
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    title: String? = null,
    onBack: () -> Unit,
    /** 强制移动端 UA，用于官方 H5 页面（如赛事主页）避免被按桌面版渲染。 */
    mobileUserAgent: Boolean = false,
    // [新增] 链接拦截回调
    onVideoClick: ((bvid: String) -> Unit)? = null,
    onSpaceClick: ((mid: Long) -> Unit)? = null,
    onLiveClick: ((roomId: Long) -> Unit)? = null,
    onDynamicClick: ((dynamicId: String) -> Unit)? = null,
    onBangumiClick: ((seasonId: Long, epId: Long, mediaId: Long) -> Unit)? = null,
    onMusicClick: ((musicId: String) -> Unit)? = null,
    onMatchClick: ((contestId: Long) -> Unit)? = null,
    onArticleCollectionClick: ((collectionId: Long) -> Unit)? = null,
    onCommentLink: ((url: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // 组合期读取明暗主题，factory 里不能再调用 @Composable。
    val shortReviewReport = remember(url) { isShortReviewReportUrl(url) }
    val darkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val webViewBackground = if (darkTheme) 0xFF101010.toInt() else 0xFFFFFFFF.toInt()
    val browserSession = remember(url) { BrowserPageSession() }
    var currentPageUrl by remember(url) { mutableStateOf(url) }
    var canGoBack by remember(url) { mutableStateOf(false) }
    val openInitialUrlExternally = remember(url) { shouldOpenWebUrlExternally(url) }
    if (openInitialUrlExternally) {
        val currentOnBack by rememberUpdatedState(onBack)
        // App-initiated loadUrl() bypasses shouldOverrideUrlLoading(). Route external
        // originals before creating AndroidView: host HWUI crashes cannot be caught here.
        // This boundary is material-independent; official in-app pages keep their rendering.
        LaunchedEffect(url) {
            runCatching { uriHandler.openUri(url.trim()) }
                .onFailure {
                    Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
                }
            // Keep the source article/navigation entry, including when no handler exists.
            currentOnBack()
        }
        return
    }

    var pendingFileWebView by remember(url) { mutableStateOf<BrowserWebView?>(null) }
    val reportImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pendingFileWebView?.finishReportFileSelection(uris)
        pendingFileWebView = null
    }
    val containerSources = com.android.purebilibili.feature.live.LocalLiveCardContainerSources.current
    val cardSource = remember(url, title) {
        containerSources?.sources?.get(com.android.purebilibili.navigation3.BiliPaiNavKey.Web(url, title.orEmpty()))
    }
    var cardPageReady by remember(url) { mutableStateOf(false) }
    val reduceCardMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val webContentAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (cardSource == null || cardPageReady) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(if (reduceCardMotion) 0 else 150),
        label = "shopWebContent",
    )
    var rendererFailed by remember(url) { mutableStateOf(false) }
    var externalUrl by remember(url) { mutableStateOf<String?>(null) }
    val canNavigateBack = canGoBack && externalUrl == null && !rendererFailed
    val goBack = { if (canNavigateBack) browserSession.goBack() else onBack() }
    androidx.activity.compose.BackHandler(enabled = canNavigateBack) { browserSession.goBack() }
    val externalOpenGate = remember(url) { LinkOpenGate() }
    val openExternal by rememberUpdatedState<(String) -> Unit>({ targetUrl ->
        // Redirect callbacks can arrive repeatedly before Compose removes AndroidView.
        if (externalOpenGate.tryAcquire(android.os.SystemClock.elapsedRealtime())) {
            runCatching { uriHandler.openUri(targetUrl) }
                .onSuccess { externalUrl = targetUrl }
                .onFailure {
                    externalOpenGate.release()
                    Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
                }
        }
    })

    AppScaffold(
        // WebView 有独立渲染树；玻璃图标和原生顶栏模糊都不能重复录制网页。
        contentBackdropEnabled = false,
        topBar = {
            AppTopBar(
                title = title ?: "浏览器",
                navigationIcon = {
                    AppIconButton(onClick = goBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { openExternal(externalUrl ?: currentPageUrl) }, modifier = Modifier.appElasticPress()) {
                        Text("外部打开")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = AppContentColors.primaryText()
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (rendererFailed || externalUrl != null) {
                Text(
                    text = if (rendererFailed) "网页渲染异常，请使用外部浏览器打开"
                        else "已交给外部应用打开，可返回评论区",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                key(url, darkTheme) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            syncBilibiliLoginCookies()
                            BrowserWebView(browserThemeContext(context, darkTheme)).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(webViewBackground)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                                    androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkTheme && !shortReviewReport)
                                }
                                if (mobileUserAgent || shortReviewReport) {
                                    settings.userAgentString = MOBILE_USER_AGENT
                                }
                                if (shortReviewReport) {
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                }

                                if (shortReviewReport) {
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onShowFileChooser(
                                            webView: WebView,
                                            filePathCallback: ValueCallback<Array<Uri>>,
                                            fileChooserParams: FileChooserParams,
                                        ): Boolean {
                                            if (!isShortReviewReportUrl(webView.url.orEmpty())) return false
                                            val browser = webView as BrowserWebView
                                            pendingFileWebView?.finishReportFileSelection(emptyList())
                                            browser.beginReportFileSelection(filePathCallback, fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
                                            pendingFileWebView = browser
                                            try {
                                                reportImagePicker.launch("image/*")
                                            } catch (_: Exception) {
                                                browser.finishReportFileSelection(emptyList())
                                                pendingFileWebView = null
                                                Toast.makeText(context, "无法打开图片选择器", Toast.LENGTH_SHORT).show()
                                            }
                                            return true
                                        }
                                    }
                                }

                                // [核心] 自定义 WebViewClient 拦截 Bilibili 链接
                                webViewClient = object : WebViewClient() {
                                    override fun doUpdateVisitedHistory(view: WebView, loadedUrl: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, loadedUrl, isReload)
                                        if (browserSession.owns(view)) {
                                            canGoBack = view.canGoBack()
                                            if (!loadedUrl.isNullOrBlank()) currentPageUrl = loadedUrl
                                        }
                                    }
                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: android.webkit.WebResourceError,
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request.isForMainFrame) cardPageReady = true
                                    }
                                    override fun onPageCommitVisible(view: WebView, loadedUrl: String?) {
                                        super.onPageCommitVisible(view, loadedUrl)
                                        cardPageReady = true
                                    }
                                    override fun onPageFinished(view: WebView, loadedUrl: String?) {
                                        super.onPageFinished(view, loadedUrl)
                                        (view as? BrowserWebView)?.applyShortReviewReportTheme(loadedUrl)
                                        cardPageReady = true
                                    }
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        if (request?.isForMainFrame != true) return false
                                        val requestUrl = request?.url?.toString() ?: return false
                                        return handleBilibiliUrl(
                                            webView = view,
                                            urlString = requestUrl,
                                            hasUserGesture = request.hasGesture()
                                        )
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView,
                                        detail: RenderProcessGoneDetail
                                    ): Boolean {
                                        android.util.Log.e("WebViewScreen", "Renderer exited: crashed=${detail.didCrash()}")
                                        (view as BrowserWebView).release(rendererGone = true)
                                        rendererFailed = true
                                        return true
                                    }

                                    // 兼容旧版 API
                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        return url?.let {
                                            handleBilibiliUrl(
                                                webView = view,
                                                urlString = it,
                                                hasUserGesture = false
                                            )
                                        } ?: false
                                    }

                                    /**
                                     * 处理 Bilibili URL 拦截
                                     * @param webView WebView 实例，用于加载转换后的 URL
                                     * @return true 表示已拦截处理，false 表示继续加载网页
                                     */
                                    private fun handleBilibiliUrl(
                                        webView: WebView?,
                                        urlString: String,
                                        hasUserGesture: Boolean
                                    ): Boolean {
                                        android.util.Log.d("WebViewScreen", "🔗 Intercepting URL: $urlString")
                                        try {
                                            val uri = android.net.Uri.parse(urlString)
                                            val scheme = uri.scheme ?: ""
                                            val host = uri.host ?: ""

                                            android.util.Log.d("WebViewScreen", "🔍 Scheme: $scheme, Host: $host")

                                            fun dispatchTarget(target: BilibiliNavigationTarget): Boolean {
                                                if (target.hasCommentLocation) {
                                                    if (onCommentLink != null) {
                                                        onCommentLink(urlString)
                                                    } else {
                                                        val intent = android.content.Intent(
                                                            android.content.Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(urlString),
                                                        ).setPackage(context.packageName)
                                                        runCatching { context.startActivity(intent) }.onFailure {
                                                            Toast.makeText(context, "无法打开评论链接", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    return true
                                                }
                                                return when (target) {
                                                    is BilibiliNavigationTarget.Video -> {
                                                        onVideoClick?.invoke(target.videoId)
                                                        onVideoClick != null
                                                    }

                                                    is BilibiliNavigationTarget.Space -> {
                                                        onSpaceClick?.invoke(target.mid)
                                                        onSpaceClick != null
                                                    }

                                                    is BilibiliNavigationTarget.Live -> {
                                                        onLiveClick?.invoke(target.roomId)
                                                        onLiveClick != null
                                                    }

                                                    is BilibiliNavigationTarget.BangumiSeason -> {
                                                        onBangumiClick?.invoke(target.seasonId, 0, target.mediaId)
                                                        onBangumiClick != null
                                                    }

                                                    is BilibiliNavigationTarget.BangumiEpisode -> {
                                                        onBangumiClick?.invoke(0, target.epId, 0)
                                                        onBangumiClick != null
                                                    }

                                                    is BilibiliNavigationTarget.Music -> {
                                                        onMusicClick?.invoke(target.musicId)
                                                        onMusicClick != null
                                                    }

                                                    is BilibiliNavigationTarget.Dynamic -> {
                                                        onDynamicClick?.invoke(target.dynamicId)
                                                        onDynamicClick != null
                                                    }

                                                    is BilibiliNavigationTarget.Match -> {
                                                        onMatchClick?.invoke(target.contestId)
                                                        onMatchClick != null
                                                    }

                                                    is BilibiliNavigationTarget.ArticleCollection -> {
                                                        onArticleCollectionClick?.invoke(target.collectionId)
                                                        onArticleCollectionClick != null
                                                    }
                                                    is BilibiliNavigationTarget.Search -> false
                                                    is BilibiliNavigationTarget.Article -> false
                                                    is BilibiliNavigationTarget.PopularFeed -> false
                                                    is BilibiliNavigationTarget.CommentDetail -> false
                                                }
                                            }

                                            when (val action = resolveWebViewNavigationAction(urlString, hasUserGesture)) {
                                                is WebViewNavigationAction.OpenExternal -> {
                                                    openExternal(action.url)
                                                    return true
                                                }
                                                is WebViewNavigationAction.Block -> {
                                                    android.util.Log.d("WebViewScreen", "⛔ Blocked navigation: $urlString")
                                                    return true
                                                }

                                                is WebViewNavigationAction.LoadInWebView -> {
                                                    android.util.Log.d("WebViewScreen", "🔄 Deep link -> ${action.url}")
                                                    webView?.loadUrl(action.url)
                                                    return true
                                                }

                                                is WebViewNavigationAction.DispatchTarget -> {
                                                    if (dispatchTarget(action.target)) {
                                                        android.util.Log.d("WebViewScreen", "✅ Routed target: ${action.target}")
                                                        return true
                                                    }
                                                }

                                                WebViewNavigationAction.AllowWebLoad -> Unit
                                            }

                                            if (scheme == "bilibili" || scheme == "bili") {
                                                android.util.Log.w("WebViewScreen", "⚠️ Blocked unknown deep link: $urlString")
                                                return true
                                            }

                                            // Let short links follow redirects normally. Re-loading an
                                            // unresolved short URL here would repeat the same interception.

                                        } catch (e: Exception) {
                                            android.util.Log.e("WebViewScreen", "URL parsing error: ${e.message}")
                                        }

                                        return false // 不拦截，继续加载
                                    }
                                }

                            }.also { browserSession.attach(it, url) }
                        },
                        update = { webView ->
                            // Compare the caller's request, not the current URL after redirects.
                            webView.setShortReviewReportTheme(if (shortReviewReport) darkTheme else null, webViewBackground)
                            webView.loadRequestedUrl(url)
                        },
                        onRelease = { browserSession.release(it) },
                        modifier = Modifier.fillMaxSize().then(
                            if (cardSource != null) Modifier.graphicsLayer { alpha = webContentAlpha.value }
                            else Modifier
                        )
                    )
                    if (cardSource != null && webContentAlpha.value < 1f) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .graphicsLayer { alpha = 1f - webContentAlpha.value }
                                .padding(24.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            coil3.compose.AsyncImage(
                                model = cardSource.previewImageUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            )
                            Text("正在加载商品…", modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                    }
                }
            }
        }
    }
}

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Version/4.0 Chrome/124.0.0.0 Mobile Safari/537.36"

/** WebView 从原生主题的 isLightTheme 读取 prefers-color-scheme，必须与应用选择保持一致。 */
internal fun browserThemeContext(context: Context, darkTheme: Boolean): Context =
    android.view.ContextThemeWrapper(context,
        if (darkTheme) com.android.purebilibili.R.style.Theme_BiliPai_Web_Dark
        else com.android.purebilibili.R.style.Theme_BiliPai_Web_Light)

/** 明暗切换需重建原生主题上下文，保留当前网页与浏览历史，避免回到最初的商品入口。 */
private class BrowserPageSession {
    private var active: BrowserWebView? = null
    private var savedState: android.os.Bundle? = null

    fun owns(view: WebView): Boolean = active === view
    fun goBack() { active?.goBack() }

    fun attach(view: BrowserWebView, requestedUrl: String) {
        active?.let { previous ->
            savedState = previous.savePageState()
        }
        active = view
        savedState?.let { view.restoreRequestedPage(it, requestedUrl) }
        savedState = null
    }

    fun release(view: BrowserWebView) {
        if (active === view) {
            savedState = view.savePageState()
            active = null
        }
        view.release()
    }
}

internal fun isShortReviewReportUrl(url: String): Boolean = runCatching {
    val uri = android.net.Uri.parse(url)
    uri.scheme == "https" && uri.host.equals("www.bilibili.com", ignoreCase = true) &&
        uri.path?.trimEnd('/') == "/appeal" && uri.getQueryParameter("type") == "shortComment"
}.getOrDefault(false)

private class BrowserWebView(context: Context) : WebView(context) {
    private var reportDarkTheme: Boolean? = null
    private var reportFileCallback: ValueCallback<Array<Uri>>? = null
    private var reportAllowsMultiple = false

    fun beginReportFileSelection(callback: ValueCallback<Array<Uri>>, allowsMultiple: Boolean) {
        reportFileCallback?.onReceiveValue(null)
        reportFileCallback = callback
        reportAllowsMultiple = allowsMultiple
    }

    fun finishReportFileSelection(uris: List<Uri>) {
        val callback = reportFileCallback
        reportFileCallback = null
        val selected = if (reportAllowsMultiple) uris else uris.take(1)
        reportAllowsMultiple = false
        callback?.onReceiveValue(selected.takeIf { it.isNotEmpty() }?.toTypedArray())
    }
    private var requestedUrl: String? = null
    private var released = false

    fun savePageState(): android.os.Bundle? =
        if (released) null else android.os.Bundle().takeIf { saveState(it) != null }

    fun restoreRequestedPage(state: android.os.Bundle, originalUrl: String) {
        if (restoreState(state) != null) requestedUrl = originalUrl
    }

    fun setShortReviewReportTheme(dark: Boolean?, background: Int) {
        if (released) return
        setBackgroundColor(background)
        if (reportDarkTheme != dark) {
            reportDarkTheme = dark
            applyShortReviewReportTheme(this.url)
        }
    }

    fun applyShortReviewReportTheme(loadedUrl: String?) {
        val dark = reportDarkTheme ?: return
        if (released || loadedUrl == null || !isShortReviewReportUrl(loadedUrl)) return
        val layoutCss = """
            #app .question { min-height: 56px !important; height: auto !important; padding: 12px 16px !important; font-size: 16px !important; line-height: 1.5 !important; overflow-wrap: anywhere; }
            #app .selector, #app .option, #app .option .child-option { font-size: 16px !important; line-height: 1.5 !important; }
            #app .option, #app .option .child-option { margin: 16px !important; }
            #app .selector .memo, #app .input .memo, #app .textarea .memo, #app .link .memo { height: auto !important; line-height: 1.5 !important; padding: 12px 16px !important; font-size: 14px !important; white-space: normal !important; overflow-wrap: anywhere; }
            #app .input .textarea-warp textarea, #app .textarea .textarea-warp textarea, #app .link input { box-sizing: border-box !important; width: calc(100% - 32px) !important; padding: 12px !important; font-size: 16px !important; line-height: 1.5 !important; }
            #app .upload-area { height: auto !important; min-height: 48px; line-height: 1.5 !important; font-size: 14px !important; padding: 8px 16px !important; overflow-wrap: anywhere; }
            #app .upload-area .add-img, #app .upload-area .img-file, #app .upload-area img:not(.close) { width: 64px !important; height: 64px !important; line-height: 64px !important; }
            #app .submit { padding: 12px 16px 24px !important; }
            #app .submit div { height: auto !important; min-height: 48px; line-height: 48px !important; font-size: 16px !important; }
        """.trimIndent()
        val darkCss = if (dark) """
            html, body, #app { background-color: #151a17 !important; color: #e1e6df !important; }
            body, #app, main, section, article, header, footer, ul, li, label { background-color: transparent !important; color: #e1e6df !important; border-color: #454c46 !important; }
            #app .question, #app .option, #app .selector, #app .memo, #app .upload-area { color: #e1e6df !important; border-color: #454c46 !important; }
            p, span, h1, h2, h3 { color: #e1e6df !important; }
            input, textarea, select { background-color: #242b26 !important; color: #e1e6df !important; border-color: #68736a !important; }
            input[type=radio], input[type=checkbox] { accent-color: #80c99a; }
        """.trimIndent() else ""
        val quotedCss = org.json.JSONObject.quote(layoutCss + "\n" + darkCss)
        evaluateJavascript("""(function(){
            var viewport = document.querySelector('meta[name=viewport]');
            if (!viewport) { viewport = document.createElement('meta'); viewport.name = 'viewport'; document.head.appendChild(viewport); }
            viewport.content = 'width=device-width, initial-scale=1';
            var style = document.getElementById('bilipai-review-report-theme');
            if (!style) { style = document.createElement('style'); style.id = 'bilipai-review-report-theme'; document.head.appendChild(style); }
            style.textContent = $quotedCss;
        })();""", null)
    }

    fun loadRequestedUrl(url: String) {
        if (released || requestedUrl == url) return
        requestedUrl = url
        loadUrl(url)
    }

    fun release(rendererGone: Boolean = false) {
        if (released) return
        released = true
        finishReportFileSelection(emptyList())
        (parent as? ViewGroup)?.removeView(this)
        if (!rendererGone) stopLoading()
        webViewClient = WebViewClient()
        webChromeClient = null
        destroy()
    }
}
