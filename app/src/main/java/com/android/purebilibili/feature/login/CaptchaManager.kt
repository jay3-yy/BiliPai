package com.android.purebilibili.feature.login

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Base64
import com.android.purebilibili.core.util.Logger
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.android.purebilibili.core.network.NetworkModule
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * 极验验证管理器 (WebView 方案)
 * 使用 WebView 加载极验验证，无需外部 SDK 依赖
 */
class CaptchaManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "CaptchaManager"
    }
    
    private var webView: WebView? = null
    private var dialog: Dialog? = null
    private var geetestConfigCall: Call? = null
    
    /**
     * 初始化并启动极验验证
     * @param gt 极验 ID (从 B站 API 获取)
     * @param challenge 极验 challenge (从 B站 API 获取)
     * @param onSuccess 验证成功回调，返回 validate 和 seccode
     * @param onFailed 验证失败回调
     * @param onCancel 用户取消回调
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun startCaptcha(
        gt: String,
        challenge: String,
        onSuccess: (validate: String, seccode: String, challenge: String) -> Unit,
        onFailed: (error: String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        try {
            Logger.d(TAG, "Starting WebView captcha")
            val isDarkMode = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            hideKeyboard()
            
            // 创建 WebView
            webView = WebView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                setBackgroundColor(Color.TRANSPARENT)
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Logger.d(TAG, "Captcha page loaded")
                    }
                }
                
                webChromeClient = WebChromeClient()
                
                // Named bridge class keeps @JavascriptInterface method names stable under R8.
                addJavascriptInterface(
                    GeetestJsBridge(
                        activity = activity,
                        dismiss = { dialog?.dismiss() },
                        onSuccess = onSuccess,
                        onFailed = onFailed,
                        onCancel = onCancel
                    ),
                    "Android"
                )
            }
            
            // 先显示加载页，再获取当前验证会话对应的动态配置。
            val html = generateLoadingHtml(isDarkMode)
            webView?.loadDataWithBaseURL(
                "https://www.bilibili.com",
                html,
                "text/html",
                "UTF-8",
                null
            )
            
            dialog = Dialog(activity).apply {
                requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                setContentView(requireNotNull(webView))
                setCanceledOnTouchOutside(true)
                setOnCancelListener {
                    geetestConfigCall?.cancel()
                    onCancel()
                }
            }
            
            dialog?.show()
            
            dialog?.window?.apply {
                val policy = resolveCaptchaDialogLayoutPolicy(
                    screenWidthPx = activity.resources.displayMetrics.widthPixels,
                    screenHeightPx = activity.resources.displayMetrics.heightPixels,
                    density = activity.resources.displayMetrics.density
                )
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setGravity(Gravity.CENTER)
                setLayout(policy.widthPx, policy.heightPx)
                decorView.setPadding(0, 0, 0, 0)
                setDimAmount(policy.dimAmount)
                setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
            }

            loadGeetestConfig(
                gt = gt,
                challenge = challenge,
                onSuccess = { configJson ->
                    val target = webView ?: return@loadGeetestConfig
                    target.loadDataWithBaseURL(
                        "https://www.bilibili.com",
                        generateGeetestHtml(configJson, isDarkMode),
                        "text/html",
                        "UTF-8",
                        null
                    )
                },
                onFailed = { error ->
                    dialog?.dismiss()
                    onFailed(error)
                }
            )
            
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e(TAG, "Failed to start captcha", e)
            onFailed("验证初始化失败: ${e.message}")
        }
    }
    
    /**
     * 生成极验验证 HTML
     */
    private fun generateLoadingHtml(dark: Boolean): String {
        val pageBg = if (dark) "#121620" else "#f5f7fb"
        val tipColor = if (dark) "#97a1b7" else "#7f889b"
        return """
            <!DOCTYPE html>
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;background:$pageBg;color:$tipColor;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;">
                <div>正在加载安全验证…</div>
            </body></html>
        """.trimIndent()
    }

    private fun loadGeetestConfig(
        gt: String,
        challenge: String,
        onSuccess: (String) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val url = "https://api.geetest.com/gettype.php".toHttpUrl().newBuilder()
            .addQueryParameter("gt", gt)
            .build()
        geetestConfigCall?.cancel()
        geetestConfigCall = NetworkModule.okHttpClient.newCall(Request.Builder().url(url).get().build())
            .also { call ->
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (call.isCanceled()) return
                        activity.runOnUiThread {
                            if (webView != null) onFailed("验证配置加载失败: ${e.message}")
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                check(it.isSuccessful) { "HTTP ${it.code}" }
                                parseGeetestConfig(
                                    raw = it.body.string(),
                                    gt = gt,
                                    challenge = challenge,
                                )
                            }
                        }
                        activity.runOnUiThread {
                            if (webView == null || call.isCanceled()) return@runOnUiThread
                            result.onSuccess(onSuccess).onFailure { error ->
                                onFailed(
                                    if (error is GeetestConfigRejectedException) error.message.orEmpty()
                                    else "验证配置解析失败: ${error.message}"
                                )
                            }
                        }
                    }
                })
            }
    }

    private fun generateGeetestHtml(configJson: String, dark: Boolean): String {
        val pageBg = if (dark) "#121620" else "#f5f7fb"
        val cardBg = if (dark) "#1b2230" else "#ffffff"
        val panelBg = if (dark) "#111722" else "#f4f7fc"
        val titleColor = if (dark) "#f2f5fb" else "#1f2431"
        val tipColor = if (dark) "#97a1b7" else "#7f889b"
        val borderColor = if (dark) "rgba(255,255,255,0.10)" else "rgba(34,50,78,0.10)"

        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>安全验证</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        html, body {
            width: 100%;
            min-height: 100%;
            background: ${pageBg};
            font-family: -apple-system, BlinkMacSystemFont, sans-serif;
            display: flex;
            justify-content: center;
            align-items: flex-start;
        }
        body {
            padding: 16px 8px;
            overflow-y: auto;
        }
        .container {
            width: min(100%, 640px);
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            padding: 24px 16px;
            background: ${cardBg};
            border-radius: 20px;
            border: 1px solid ${borderColor};
            box-shadow: 0 12px 40px rgba(0,0,0,0.22);
        }
        .title {
            font-size: 18px;
            font-weight: 600;
            color: ${titleColor};
            margin-bottom: 12px;
        }
        #captcha-container {
            width: 100%;
            max-width: 600px;
            background: ${panelBg};
            border-radius: 14px;
            padding: 16px;
            border: 1px solid ${borderColor};
        }
        .loading {
            text-align: center;
            color: ${tipColor};
            padding: 40px 0;
            font-size: 14px;
        }
        .loading::after {
            content: '';
            display: inline-block;
            width: 16px;
            height: 16px;
            border: 2px solid #fb7299;
            border-top-color: transparent;
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
            margin-left: 8px;
            vertical-align: middle;
        }
        @keyframes spin {
            to { transform: rotate(360deg); }
        }
        .tip {
            text-align: center;
            color: ${tipColor};
            font-size: 12px;
            margin-top: 10px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="title">请完成验证</div>
        <div id="captcha-container">
            <div class="loading">加载中</div>
        </div>
        <div class="tip">点击图片上的文字完成验证</div>
    </div>

    <script src="https://static.geetest.com/static/js/fullpage.0.0.0.js"></script>
    <script>
        var geetestConfig = $configJson;
        var captchaObj = window.Geetest(geetestConfig)
            .onReady(function() {
                document.querySelector('.loading').style.display = 'none';
                captchaObj.verify();
            })
            .onSuccess(function() {
                var result = captchaObj.getValidate();
                if (result) {
                    window.Android.onCaptchaSuccess(
                        result.geetest_validate,
                        result.geetest_seccode,
                        result.geetest_challenge || geetestConfig.challenge
                    );
                } else {
                    window.Android.onCaptchaFailed("验证结果为空");
                }
            })
            .onError(function(e) {
                window.Android.onCaptchaFailed(e.msg || e.error_code || "验证失败");
            })
            .onClose(function() {
                window.Android.onCaptchaCancel();
            });
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun hideKeyboard() {
        try {
            val imm = activity.getSystemService(InputMethodManager::class.java)
            val token = activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken
            imm?.hideSoftInputFromWindow(token, 0)
            activity.currentFocus?.clearFocus()
        } catch (_: Exception) {
        }
    }
    
    /**
     * 销毁资源
     */
    fun destroy() {
        geetestConfigCall?.cancel()
        geetestConfigCall = null
        dialog?.dismiss()
        webView?.destroy()
        webView = null
        dialog = null
    }
}

/**
 * Geetest WebView → Android bridge. Must stay a concrete class so release R8
 * does not rename the methods invoked from JavaScript.
 */
internal class GeetestJsBridge(
    private val activity: Activity,
    private val dismiss: () -> Unit,
    private val onSuccess: (validate: String, seccode: String, challenge: String) -> Unit,
    private val onFailed: (error: String) -> Unit,
    private val onCancel: () -> Unit
) {
    @JavascriptInterface
    fun onCaptchaSuccess(validate: String, seccode: String, newChallenge: String) {
        Logger.d("CaptchaManager", "Captcha success via JS")
        activity.runOnUiThread {
            dismiss()
            onSuccess(validate, seccode, newChallenge)
        }
    }

    @JavascriptInterface
    fun onCaptchaFailed(error: String) {
        Logger.e("CaptchaManager", "Captcha failed via JS: $error")
        activity.runOnUiThread {
            dismiss()
            onFailed(error)
        }
    }

    @JavascriptInterface
    fun onCaptchaCancel() {
        Logger.d("CaptchaManager", "Captcha cancelled")
        activity.runOnUiThread {
            dismiss()
            onCancel()
        }
    }
}

/**
 * RSA 加密工具
 * 用于密码登录时加密密码
 */
object RsaEncryption {
    private const val TAG = "RsaEncryption"
    
    /**
     * 使用 RSA 公钥加密密码
     * @param password 原始密码
     * @param publicKey RSA 公钥 (PEM 格式)
     * @param salt 盐值 (hash)
     * @return Base64 编码的加密密码
     */
    fun encryptPassword(password: String, publicKey: String, salt: String): String? {
        return encrypt(salt + password, publicKey)
    }

    fun encrypt(value: String, publicKey: String): String? {
        return try {
            // 处理公钥字符串
            val keyStr = publicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s+".toRegex(), "")
            
            // 解码公钥
            val keyBytes = Base64.decode(keyStr, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val pubKey = keyFactory.generatePublic(keySpec)
            
            // 加密 (salt + password)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, pubKey)
            val encryptedBytes = cipher.doFinal(value.toByteArray())
            
            // Base64 编码
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e(TAG, "Failed to encrypt password", e)
            null
        }
    }
}
