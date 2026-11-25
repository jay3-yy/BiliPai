
package com.android.purebilibili.feature.login

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.android.purebilibili.core.util.Logger
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.CaptchaData
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LoginState {
    object Loading : LoginState()
    data class QrCode(val bitmap: Bitmap) : LoginState()
    data class Scanned(val bitmap: Bitmap) : LoginState()
    object Success : LoginState()
    object HighQualityAuthorization : LoginState()
    data class Error(val msg: String) : LoginState()
    
    //  手机号登录状态
    object PhoneIdle : LoginState()  // 等待输入手机号
    data class CaptchaReady(val captchaData: CaptchaData) : LoginState()  // 验证码准备就绪
    data class SmsSent(val captchaKey: String) : LoginState()  // 短信已发送
    object PasswordMode : LoginState()  // 密码登录模式

    /** Password login hit status=2 risk check; show bound phone and send SMS. */
    data class RiskVerificationRequired(
        val hideTel: String,
        val message: String,
        val errorMessage: String? = null,
    ) : LoginState()

    /** Risk-flow geetest ready (from safecenter/captcha/pre). */
    data class RiskCaptchaReady(
        val hideTel: String,
        val gt: String,
        val challenge: String,
        val recaptchaToken: String,
    ) : LoginState()

    /** Risk SMS already sent; waiting for user code. */
    data class RiskSmsSent(
        val hideTel: String,
        val captchaKey: String,
        val errorMessage: String? = null,
    ) : LoginState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state = _state.asStateFlow()

    private val _phoneRegions = MutableStateFlow(resolveFallbackPhoneRegions())
    val phoneRegions = _phoneRegions.asStateFlow()

    private var qrcodeKey: String = ""
    private var isPolling = true

    /**
     *  [重构] 统一使用 TV 端二维码登录
     * 这样登录后自动获得 access_token，支持 4K/HDR/1080P60 高画质视频
     */
    fun loadQrCode() {
        //  直接调用 TV 登录，获取 access_token
        loadTvQrCode()
    }
    
    /**
     * [保留] 原 Web 端二维码登录 (作为备用)
     */
    private var webPollJob: kotlinx.coroutines.Job? = null
    private var tvPollJob: kotlinx.coroutines.Job? = null

    fun loadWebQrCode() {
        webPollJob?.cancel()
        tvPollJob?.cancel()
        isTvPolling = false
        isPolling = true
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                Logger.d("LoginDebug", "1. 开始获取 Web 二维码...")

                val resp = NetworkModule.passportApi.generateQrCode()

                //  核心修复：处理可空类型
                val data = resp.data ?: throw Exception("服务器返回数据为空")
                val url = data.url ?: throw Exception("二维码 URL 为空")

                //  这里使用 ?: 抛出异常，解决了 Type mismatch 问题
                qrcodeKey = data.qrcode_key ?: throw Exception("二维码 Key 为空")

                Logger.d("LoginDebug", "Web 二维码获取成功")
                val bitmap = generateQrBitmap(url)
                currentBitmap = bitmap //  保存以便在 Scanned 状态使用
                _state.value = LoginState.QrCode(bitmap)

                startPolling()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("LoginDebug", "获取二维码失败", e)
                _state.value = LoginState.Error(e.message ?: "网络错误")
            }
        }
    }

    private var currentBitmap: Bitmap? = null //  保存当前二维码用于 Scanned 状态

    private fun startPolling() {
        webPollJob?.cancel()
        webPollJob = viewModelScope.launch {
            Logger.d("LoginDebug", "3. 开始轮询...")
            while (isPolling) {
                delay(2000) //  缩短轮询间隔，更快响应
                try {
                    val response = NetworkModule.passportApi.pollQrCode(qrcodeKey)
                    val body = response.body()

                    //  核心修复：处理可空类型，默认为 -1 防止空指针
                    val code = body?.data?.code ?: -1

                    Logger.d("LoginDebug", "轮询状态: Code=$code")

                    when (code) {
                        0 -> {
                            //  登录成功
                            Logger.d("LoginDebug", ">>> 登录成功！开始解析 Cookie <<<")

                            val cookies = response.headers().values("Set-Cookie")
                            var sessData = ""
                            var biliJct = "" //  CSRF token

                            for (line in cookies) {
                                if (line.contains("SESSDATA")) {
                                    val parts = line.split(";")
                                    for (part in parts) {
                                        val trimPart = part.trim()
                                        if (trimPart.startsWith("SESSDATA=")) {
                                            sessData = trimPart.substringAfter("SESSDATA=")
                                            break
                                        }
                                    }
                                }
                                //  提取 bili_jct (CSRF Token)
                                if (line.contains("bili_jct")) {
                                    val parts = line.split(";")
                                    for (part in parts) {
                                        val trimPart = part.trim()
                                        if (trimPart.startsWith("bili_jct=")) {
                                            biliJct = trimPart.substringAfter("bili_jct=")
                                            break
                                        }
                                    }
                                }
                            }

                            if (sessData.isNotEmpty()) {
                                Logger.d("LoginDebug", " 成功提取 SESSDATA")
                                Logger.d("LoginDebug", " 成功提取 bili_jct=${biliJct.isNotEmpty()}")

                                val cookieValues = cookies.mapNotNull { line ->
                                    line.substringBefore(';').split('=', limit = 2)
                                        .takeIf { it.size == 2 }?.let { it[0].trim() to it[1] }
                                }.toMap()
                                isPolling = false
                                completeLogin(
                                    sessData = sessData, csrf = biliJct,
                                    buvid3 = cookieValues["buvid3"].orEmpty(),
                                    source = "qrcode_web", cookies = cookieValues,
                                )
                            } else {
                                _state.value = LoginState.Error("Cookie 解析失败")
                            }
                        }
                        86090 -> {
                            //  新增: 已扫描待确认
                            Logger.d("LoginDebug", " 二维码已扫描，等待确认...")
                            currentBitmap?.let { bitmap ->
                                withContext(Dispatchers.Main.immediate) {
                                    _state.value = LoginState.Scanned(bitmap)
                                }
                            }
                        }
                        86038 -> {
                            // 二维码已过期
                            _state.value = LoginState.Error("二维码已过期，请刷新")
                            isPolling = false
                        }
                        86101 -> {
                            // 未扫描，继续轮询
                            Logger.d("LoginDebug", "等待扫描...")
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    if (!isPolling) _state.value = LoginState.Error(e.message ?: "登录态校验失败")
                    com.android.purebilibili.core.util.Logger.e("LoginDebug", "轮询异常", e)
                }
            }
        }
    }

    fun stopPolling() {
        isPolling = false
        isTvPolling = false
        webPollJob?.cancel()
        webPollJob = null
        tvPollJob?.cancel()
        tvPollJob = null
    }

    fun showLoginError(message: String) {
        if (riskTmpCode.isNotBlank()) {
            restoreRiskVerificationUi(errorMessage = message)
        } else {
            _state.value = LoginState.Error(message)
        }
    }

    /** Keep risk session and return to the bound-phone SMS step. */
    fun restoreRiskVerificationUi(errorMessage: String? = null) {
        if (riskTmpCode.isBlank()) {
            _state.value = LoginState.Error(errorMessage ?: "风控会话已失效，请重新密码登录")
            return
        }
        _state.value = LoginState.RiskVerificationRequired(
            hideTel = riskHideTel.ifBlank { "已绑定手机号" },
            message = "本次登录环境存在风险，需使用手机号进行验证",
            errorMessage = errorMessage,
        )
    }

    private fun generateQrBitmap(content: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val w = bitMatrix.width
        val h = bitMatrix.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        for (x in 0 until w) {
            for (y in 0 until h) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
    
    // ==========  手机号登录方法 ==========
    
    // 当前验证码数据 (极验验证成功后暂存)
    private var currentCaptchaData: CaptchaData? = null
    private var currentValidate: String = ""
    private var currentSeccode: String = ""
    private var currentChallenge: String = ""
    private var currentCaptchaKey: String = ""  // 发送短信后返回的 key
    private var currentPhone: String = ""
    /** PiliPlus App SMS cid = country_id，中国大陆 = 86 */
    private var currentCountryCode: Int = 86
    // Passport's Android-HD identity is intentionally separate from the web
    // cookie jar's buvid3; see the equivalent PiliPlus LoginHttp identity.
    private val appLoginIdentity = PiliPlusLoginIdentityStore.get(application)
    private val appLoginDeviceId = appLoginIdentity.deviceId
    private val appLoginBuvid = appLoginIdentity.buvid

    // Password-login risk verification (safe center)
    private var riskTmpCode: String = ""
    private var riskRequestId: String = ""
    private var riskSource: String = "risk"
    private var riskRefererUrl: String = ""
    private var riskCaptchaKey: String = ""
    private var riskHideTel: String = ""
    private var riskRecaptchaToken: String = ""

    /**
     * 拉取 passport 国际冠字码列表（common + others）。
     * 失败时保留离线兜底，保证登录页仍可选主要地区。
     */
    fun loadPhoneRegions() {
        viewModelScope.launch {
            try {
                val response = NetworkModule.passportApi.getCountryList()
                val data = response.data
                if (response.code == 0 && data != null) {
                    val mapped = mapPassportCountryListToPhoneRegions(data)
                    if (mapped.isNotEmpty()) {
                        _phoneRegions.value = mapped
                        Logger.d("LoginDebug", "国家列表加载成功: ${mapped.size}")
                    }
                } else {
                    Logger.d(
                        "LoginDebug",
                        "国家列表返回异常 code=${response.code} msg=${response.message}"
                    )
                }
            } catch (e: Exception) {
                Logger.e("LoginDebug", "国家列表加载失败，使用离线兜底", e)
            }
        }
    }
    
    /**
     * 获取极验验证参数
     */
    fun getCaptcha() {
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                Logger.d("LoginDebug", "获取极验验证参数...")
                
                val response = NetworkModule.passportApi.getCaptcha()
                if (response.code == 0 && response.data != null) {
                    val checkedResponseData = requireNotNull(response.data)
                    currentCaptchaData = checkedResponseData
                    Logger.d("LoginDebug", "极验参数获取成功")
                    _state.value = LoginState.CaptchaReady(checkedResponseData)
                } else {
                    _state.value = LoginState.Error("获取验证参数失败: ${response.message}")
                }
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("LoginDebug", "获取验证参数异常", e)
                _state.value = LoginState.Error("网络错误: ${e.message}")
            }
        }
    }
    
    /**
     * 保存极验验证结果
     */
    fun saveCaptchaResult(validate: String, seccode: String, challenge: String) {
        currentValidate = validate
        currentSeccode = seccode
        currentChallenge = challenge
        Logger.d("LoginDebug", "极验验证成功")
    }
    
    /**
     * 发送短信验证码
     */
    fun beginSmsCodeRequest(phone: String, countryCode: Int) {
        clearCaptchaChallenge()
        currentCaptchaKey = ""
        sendSmsCode(phone, countryCode)
    }

    fun sendSmsCode(phone: String, countryCode: Int) {
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                currentPhone = phone
                currentCountryCode = countryCode
                
                Logger.d("LoginDebug", "发送短信验证码请求")
                
                val timestampMillis = System.currentTimeMillis()
                val loginSessionId = com.android.purebilibili.core.network.AppSignUtils
                    .createLoginSessionId(appLoginBuvid, timestampMillis)
                val params = buildAndroidSmsSendParams(
                    phone = phone,
                    countryCode = countryCode,
                    token = currentCaptchaData?.token,
                    challenge = currentChallenge,
                    validate = currentValidate,
                    seccode = currentSeccode,
                    buvid = appLoginBuvid,
                    loginSessionId = loginSessionId,
                    timestampSeconds = timestampMillis / 1000
                )
                val response = NetworkModule.passportApi.sendSmsCodeByApp(
                    loginBuvid = appLoginBuvid,
                    params = com.android.purebilibili.core.network.AppSignUtils
                        .signForAndroidHdLogin(params),
                )
                
                val recaptchaUrl = response.data?.recaptchaUrl.orEmpty()
                val captchaKey = response.data?.captchaKey.orEmpty()
                if (response.code == 0 && recaptchaUrl.isBlank() && captchaKey.isNotBlank()) {
                    currentCaptchaKey = captchaKey
                    Logger.d("LoginDebug", "短信验证码已发送")
                    _state.value = LoginState.SmsSent(currentCaptchaKey)
                } else if (recaptchaUrl.isNotBlank() && restartCaptchaFrom(recaptchaUrl)) {
                    Logger.d("LoginDebug", "短信验证码要求重新完成安全验证")
                } else if ((response.code == 0 || response.code == CAPTCHA_RETRY_CODE) &&
                    prepareFallbackSmsCaptcha()
                ) {
                    Logger.d("LoginDebug", "短信登录改用备用安全验证")
                } else {
                    Logger.w(
                        "LoginDebug",
                        "短信发送失败 code=${response.code}, " +
                            "hasRecaptchaUrl=${recaptchaUrl.isNotBlank()}, hasCaptchaKey=${captchaKey.isNotBlank()}"
                    )
                    _state.value = LoginState.Error(
                        "短信发送失败(${response.code}): ${response.message} " +
                            "[${com.android.purebilibili.BuildConfig.BUILD_TYPE}/" +
                            "${com.android.purebilibili.core.network.AppSignUtils.ANDROID_HD_APP_KEY.take(8)}]"
                    )
                }
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("LoginDebug", "发送短信异常", e)
                _state.value = LoginState.Error("网络错误: ${e.message}")
            }
        }
    }
    
    /**
     * 短信验证码登录
     */
    fun loginBySms(code: Int) {
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                Logger.d("LoginDebug", "短信验证码登录请求")

                if (currentCaptchaKey.isBlank()) {
                    _state.value = LoginState.Error("短信登录会话已失效，请重新获取验证码")
                    return@launch
                }

                val keyResponse = NetworkModule.passportApi.getWebKey()
                val publicKey = keyResponse.data?.key
                if (keyResponse.code != 0 || publicKey.isNullOrBlank()) {
                    _state.value = LoginState.Error("获取登录密钥失败: ${keyResponse.message}")
                    return@launch
                }
                val encryptedDeviceToken = RsaEncryption.encrypt(
                    value = createPiliPlusRandomString(16),
                    publicKey = publicKey
                ) ?: run {
                    _state.value = LoginState.Error("生成登录设备凭据失败")
                    return@launch
                }
                
                val params = buildAndroidSmsLoginParams(
                    phone = currentPhone,
                    countryCode = currentCountryCode,
                    code = code,
                    captchaKey = currentCaptchaKey,
                    buvid = appLoginBuvid,
                    deviceId = appLoginDeviceId,
                    encryptedDeviceToken = encryptedDeviceToken,
                    timestampSeconds = com.android.purebilibili.core.network.AppSignUtils.getTimestamp()
                )
                val response = NetworkModule.passportApi.loginBySmsApp(
                    loginBuvid = appLoginBuvid,
                    params = com.android.purebilibili.core.network.AppSignUtils
                        .signForAndroidHdLogin(params),
                )
                
                val body = response.body()
                if (body?.code == 0) {
                    handleLoginResponse(
                        response = response,
                        source = "phone",
                        accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD
                    )
                } else {
                    _state.value = LoginState.Error(
                        "登录失败(${body?.code}): ${body?.message ?: "未知错误"} " +
                            "[${com.android.purebilibili.BuildConfig.BUILD_TYPE}]"
                    )
                }
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("LoginDebug", "短信登录异常", e)
                _state.value = LoginState.Error("网络错误: ${e.message}")
            }
        }
    }
    
    /**
     * 密码登录
     */
    fun beginPasswordLogin(username: String, password: String) {
        clearCaptchaChallenge()
        loginByPassword(username, password)
    }

    fun loginByPassword(phone: String, password: String) {
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                Logger.d("LoginDebug", "密码登录请求")
                
                // 1. 获取 RSA 公钥
                val keyResponse = NetworkModule.passportApi.getWebKey()
                val keyData = keyResponse.data
                if (keyResponse.code != 0 || keyData == null) {
                    _state.value = LoginState.Error("获取密钥失败: ${keyResponse.message}")
                    return@launch
                }
                
                val hash = keyData.hash
                val key = keyData.key
                
                // 2. RSA 加密密码
                val encryptedPassword = RsaEncryption.encryptPassword(password, key, hash)
                if (encryptedPassword == null) {
                    _state.value = LoginState.Error("密码加密失败")
                    return@launch
                }
                val encryptedDeviceToken = RsaEncryption.encrypt(
                    value = createPiliPlusRandomString(16),
                    publicKey = key
                ) ?: run {
                    _state.value = LoginState.Error("生成登录设备凭据失败")
                    return@launch
                }
                
                // 3. 先发起 App 登录；若服务端返回 -105，再按其 URL 完成专属验证并重试。
                val params = buildAndroidPasswordLoginParams(
                    username = phone,
                    encryptedPassword = encryptedPassword,
                    token = currentCaptchaData?.token,
                    challenge = currentChallenge,
                    validate = currentValidate,
                    seccode = currentSeccode,
                    buvid = appLoginBuvid,
                    deviceId = appLoginDeviceId,
                    encryptedDeviceToken = encryptedDeviceToken,
                    timestampSeconds = com.android.purebilibili.core.network.AppSignUtils.getTimestamp()
                )
                val response = NetworkModule.passportApi.loginByPasswordApp(
                    loginBuvid = appLoginBuvid,
                    params = com.android.purebilibili.core.network.AppSignUtils
                        .signForAndroidHdLogin(params),
                )
                
                val body = response.body()
                if (body?.code == 0) {
                    if (isPasswordLoginRiskChallenge(body.data)) {
                        beginPasswordRiskVerification(body.data!!)
                    } else {
                        handleLoginResponse(
                            response = response,
                            source = "password",
                            accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD
                        )
                    }
                } else if (body?.code == CAPTCHA_RETRY_CODE && restartCaptchaFrom(body.data?.url.orEmpty())) {
                    Logger.d("LoginDebug", "密码登录要求重新完成安全验证")
                } else {
                    _state.value = LoginState.Error(
                        "登录失败(${body?.code}): ${body?.message ?: "未知错误"} " +
                            "[${com.android.purebilibili.BuildConfig.BUILD_TYPE}]"
                    )
                }
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("LoginDebug", "密码登录异常", e)
                _state.value = LoginState.Error("网络错误: ${e.message}")
            }
        }
    }

    /**
     * Password login status=2: load bound phone info and prompt SMS risk check.
     */
    private suspend fun beginPasswordRiskVerification(
        data: com.android.purebilibili.data.model.response.LoginData
    ) {
        val riskParams = parseRiskVerifyUrl(data.url) ?: run {
            _state.value = LoginState.Error(
                "登录环境存在风险，但验证参数缺失。请改用扫码或 Cookie 导入。"
            )
            return
        }
        riskTmpCode = riskParams.tmpToken
        riskRequestId = riskParams.requestId
        riskSource = riskParams.source
        riskRefererUrl = riskParams.refererUrl
        riskCaptchaKey = ""
        riskRecaptchaToken = ""

        try {
            val info = NetworkModule.passportApi.safeCenterGetInfo(tmpCode = riskTmpCode)
            val account = info.data?.accountInfo
            if (info.code != 0 || account == null) {
                _state.value = LoginState.Error(
                    "获取安全验证信息失败(${info.code}): ${info.message.ifBlank { "请改用扫码登录" }}"
                )
                return
            }
            if (!account.telVerify) {
                _state.value = LoginState.Error(
                    "当前账号不支持手机号风控验证，请改用扫码或 Cookie 导入。"
                )
                return
            }
            riskHideTel = account.hideTel.ifBlank { "已绑定手机号" }
            val message = data.message.ifBlank {
                "本次登录环境存在风险，需使用手机号进行验证"
            }
            Logger.d("LoginDebug", "密码登录触发风控，hideTel=$riskHideTel")
            _state.value = LoginState.RiskVerificationRequired(
                hideTel = riskHideTel,
                message = message,
            )
        } catch (e: Exception) {
            Logger.e("LoginDebug", "安全中心信息获取失败", e)
            _state.value = LoginState.Error("安全验证准备失败: ${e.message}")
        }
    }

    /**
     * Start risk-flow geetest via safecenter/captcha/pre.
     */
    fun prepareRiskSmsCaptcha() {
        viewModelScope.launch {
            try {
                if (riskTmpCode.isBlank()) {
                    _state.value = LoginState.Error("风控会话已失效，请重新密码登录")
                    return@launch
                }
                _state.value = LoginState.Loading
                val pre = NetworkModule.passportApi.safeCenterPreCapture()
                val data = pre.data
                if (pre.code != 0 || data == null ||
                    data.geeGt.isBlank() || data.geeChallenge.isBlank() || data.recaptchaToken.isBlank()
                ) {
                    _state.value = LoginState.Error(
                        "获取风控验证码失败(${pre.code}): ${pre.message.ifBlank { "请改用扫码登录" }}"
                    )
                    return@launch
                }
                riskRecaptchaToken = data.recaptchaToken
                _state.value = LoginState.RiskCaptchaReady(
                    hideTel = riskHideTel,
                    gt = data.geeGt,
                    challenge = data.geeChallenge,
                    recaptchaToken = data.recaptchaToken,
                )
            } catch (e: Exception) {
                Logger.e("LoginDebug", "风控极验准备失败", e)
                _state.value = LoginState.Error("网络错误: ${e.message}")
            }
        }
    }

    /**
     * After risk geetest success, send safe-center SMS.
     */
    fun sendRiskSmsCode(validate: String, seccode: String, challenge: String) {
        viewModelScope.launch {
            try {
                if (riskTmpCode.isBlank() || riskRecaptchaToken.isBlank()) {
                    _state.value = LoginState.Error("风控会话已失效，请重新密码登录")
                    return@launch
                }
                _state.value = LoginState.Loading
                val params = buildSafeCenterSmsSendParams(
                    tmpCode = riskTmpCode,
                    recaptchaToken = riskRecaptchaToken,
                    challenge = challenge,
                    validate = validate,
                    seccode = seccode,
                )
                val signed = com.android.purebilibili.core.network.AppSignUtils
                    .signForAndroidHdLogin(params)
                val response = NetworkModule.passportApi.safeCenterSendSms(
                    referer = riskRefererUrl,
                    params = signed,
                )
                if (response.code == 0 && response.data?.captchaKey.orEmpty().isNotBlank()) {
                    riskCaptchaKey = response.data!!.captchaKey
                    Logger.d("LoginDebug", "风控短信已发送")
                    _state.value = LoginState.RiskSmsSent(
                        hideTel = riskHideTel,
                        captchaKey = riskCaptchaKey,
                    )
                } else {
                    restoreRiskVerificationUi(
                        errorMessage = "风控短信发送失败(${response.code}): " +
                            response.message.ifBlank { "请改用扫码登录" }
                    )
                }
            } catch (e: Exception) {
                Logger.e("LoginDebug", "风控短信发送异常", e)
                restoreRiskVerificationUi(errorMessage = "网络错误: ${e.message}")
            }
        }
    }

    /**
     * Submit risk SMS code, exchange oauth code for cookies/token.
     */
    fun verifyRiskSmsCode(code: String) {
        viewModelScope.launch {
            try {
                val trimmed = code.trim()
                if (trimmed.isEmpty()) {
                    _state.value = LoginState.Error("请输入短信验证码")
                    return@launch
                }
                if (riskTmpCode.isBlank() || riskCaptchaKey.isBlank()) {
                    _state.value = LoginState.Error("请先获取风控验证码")
                    return@launch
                }
                _state.value = LoginState.Loading

                val verifyParams = buildSafeCenterSmsVerifyParams(
                    code = trimmed,
                    tmpCode = riskTmpCode,
                    requestId = riskRequestId,
                    source = riskSource,
                    captchaKey = riskCaptchaKey,
                )
                val verifySigned = com.android.purebilibili.core.network.AppSignUtils
                    .signForAndroidHdLogin(verifyParams)
                val verifyResponse = NetworkModule.passportApi.safeCenterVerifySms(
                    referer = riskRefererUrl,
                    params = verifySigned,
                )
                val exchangeCode = verifyResponse.data?.code.orEmpty()
                if (verifyResponse.code != 0 || exchangeCode.isBlank()) {
                    _state.value = LoginState.RiskSmsSent(
                        hideTel = riskHideTel,
                        captchaKey = riskCaptchaKey,
                        errorMessage = "风控验证失败(${verifyResponse.code}): " +
                            verifyResponse.message.ifBlank { "验证码错误" },
                    )
                    return@launch
                }

                val tokenParams = buildOauth2AccessTokenParams(
                    code = exchangeCode,
                    buvid = appLoginBuvid,
                    timestampSeconds = com.android.purebilibili.core.network.AppSignUtils.getTimestamp(),
                )
                val tokenResponse = NetworkModule.passportApi.oauth2AccessToken(
                    com.android.purebilibili.core.network.AppSignUtils.signForAndroidHdLogin(tokenParams)
                )
                val body = tokenResponse.body()
                if (body?.code == 0) {
                    clearRiskSession()
                    handleLoginResponse(
                        response = tokenResponse,
                        source = "password_risk",
                        accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD,
                    )
                } else {
                    _state.value = LoginState.Error(
                        "换取登录态失败(${body?.code}): ${body?.message ?: "请改用扫码登录"}"
                    )
                }
            } catch (e: Exception) {
                Logger.e("LoginDebug", "风控短信验证异常", e)
                _state.value = LoginState.Error("网络错误: ${e.message}")
            }
        }
    }

    private fun clearRiskSession() {
        riskTmpCode = ""
        riskRequestId = ""
        riskSource = "risk"
        riskRefererUrl = ""
        riskCaptchaKey = ""
        riskHideTel = ""
        riskRecaptchaToken = ""
    }

    /**
     * Mirrors PiliPlus: a -105 response carries a replacement captcha URL.
     * Preserve the original login request in the screen so it can be replayed
     * only after this new challenge succeeds.
     */
    private fun restartCaptchaFrom(recaptchaUrl: String): Boolean {
        val captchaData = parseLoginRecaptchaUrl(recaptchaUrl) ?: return false
        currentCaptchaData = captchaData
        currentValidate = ""
        currentSeccode = ""
        currentChallenge = ""
        _state.value = LoginState.CaptchaReady(captchaData)
        return true
    }

    private fun clearCaptchaChallenge() {
        currentCaptchaData = null
        currentValidate = ""
        currentSeccode = ""
        currentChallenge = ""
    }

    /** PiliPlus fallback when the SMS response asks for captcha without a usable recaptcha_url. */
    private suspend fun prepareFallbackSmsCaptcha(): Boolean {
        val response = runCatching { NetworkModule.passportApi.safeCenterPreCapture() }
            .getOrNull() ?: return false
        val data = response.data ?: return false
        if (response.code != 0 || data.recaptchaToken.isBlank() ||
            data.geeGt.isBlank() || data.geeChallenge.isBlank()
        ) {
            return false
        }
        currentCaptchaData = CaptchaData(
            token = data.recaptchaToken,
            geetest = com.android.purebilibili.data.model.response.GeetestData(
                gt = data.geeGt,
                challenge = data.geeChallenge,
            ),
            type = "geetest",
        )
        currentValidate = ""
        currentSeccode = ""
        currentChallenge = ""
        _state.value = LoginState.CaptchaReady(requireNotNull(currentCaptchaData))
        return true
    }
    
    /**
     * 处理登录返回的 Cookie
     */
    private suspend fun handleLoginResponse(
        response: retrofit2.Response<com.android.purebilibili.data.model.response.LoginResponse>,
        source: String,
        accessTokenPlatform: String = TokenManager.ACCESS_TOKEN_PLATFORM_TV
    ) {
        val body = response.body() ?: run {
            _state.value = LoginState.Error("登录响应为空")
            return
        }
        if (isPasswordLoginRiskChallenge(body.data)) {
            beginPasswordRiskVerification(body.data!!)
            return
        }
        val cookies = response.headers().values("Set-Cookie")
            .map { it.substringBefore(';') }
            .mapNotNull { item ->
                item.trim().takeIf { it.contains('=') }?.let {
                    it.substringBefore('=') to it.substringAfter('=')
                }
            }
            .toMap()
            .toMutableMap()
        body.data?.cookieInfo?.cookies.orEmpty().forEach { cookie ->
            cookies[cookie.name] = cookie.value
        }

        val sessData = cookies["SESSDATA"].orEmpty()
        val biliJct = cookies["bili_jct"].orEmpty()
        
        if (sessData.isNotEmpty()) {
            completeLogin(
                sessData = sessData,
                csrf = biliJct,
                buvid3 = cookies["buvid3"].orEmpty(),
                accessToken = body.data?.tokenInfo?.accessToken.orEmpty(),
                refreshToken = body.data?.tokenInfo?.refreshToken.orEmpty(),
                accessTokenPlatform = accessTokenPlatform,
                source = source,
                cookies = cookies,
            )
        } else {
            val riskHint = body.data?.message?.takeIf { it.isNotBlank() }
            _state.value = LoginState.Error(
                riskHint?.let { "登录未返回 Cookie：$it。可改用扫码或 Cookie 导入。" }
                    ?: "Cookie 解析失败，可改用扫码或 Cookie 导入。"
            )
        }
    }

    private suspend fun finishLogin(source: String) {
        syncCurrentAccountSession()
        withContext(Dispatchers.Main.immediate) {
            _state.value = LoginState.Success
            com.android.purebilibili.core.util.AnalyticsHelper.logLogin(source)
            com.android.purebilibili.core.util.AnalyticsHelper.syncUserContext(
                mid = TokenManager.midCache,
                isVip = TokenManager.isVipCache,
                privacyModeEnabled = com.android.purebilibili.core.store.SettingsManager
                    .isPrivacyModeEnabledSync(getApplication())
            )
        }
    }

    /** Finish the already imported session using the same post-login flow as other methods. */
    fun completeTransferredLogin() {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                finishLogin("bilipai_transfer")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = LoginState.Error(error.message ?: "账号已导入，完成登录失败，请重试")
            }
        }
    }

    fun continueWithStandardSession() {
        _state.value = LoginState.Success
    }

    private suspend fun completeLogin(
        sessData: String,
        csrf: String = "",
        buvid3: String = "",
        mid: Long = 0L,
        accessToken: String = "",
        refreshToken: String = "",
        accessTokenPlatform: String = TokenManager.ACCESS_TOKEN_PLATFORM_TV,
        source: String,
        cookies: Map<String, String> = emptyMap(),
    ) {
        com.android.purebilibili.data.repository.SessionRepository.save(
            context = getApplication(), sessData = sessData, csrf = csrf, buvid3 = buvid3,
            mid = mid, accessToken = accessToken, refreshToken = refreshToken,
            accessTokenPlatform = accessTokenPlatform, cookies = cookies,
        )
        finishLogin(source)
    }

    fun loginByCookie(rawCookieHeader: String) {
        val importedCookies = parseLoginCookieHeader(rawCookieHeader)
        if (importedCookies == null) {
            _state.value = LoginState.Error("Cookie 中缺少 SESSDATA")
            return
        }

        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                val response = NetworkModule.passportApi
                    .validateCookieSession(importedCookies.toCookieHeader())
                val navData = response.data
                if (response.code != 0 || navData == null || !navData.isLogin || navData.mid <= 0L) {
                    _state.value = LoginState.Error("Cookie 无效或已过期")
                    return@launch
                }

                completeLogin(
                    sessData = importedCookies.sessData,
                    csrf = importedCookies.csrf.orEmpty(),
                    buvid3 = importedCookies.buvid3.orEmpty(),
                    mid = navData.mid,
                    source = "cookie_import",
                    cookies = importedCookies.values,
                )
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("LoginDebug", "Cookie 验证失败", e)
                _state.value = LoginState.Error("Cookie 验证失败，请检查内容后重试")
            }
        }
    }

    private suspend fun syncCurrentAccountSession() {
        NetworkModule.clearRuntimeCookies()
        val response = NetworkModule.api.getNavInfo()
        val navData = response.data
        check(response.code == 0 && navData != null && navData.isLogin && navData.mid > 0L) {
            "登录态校验失败，请重新登录"
        }
        TokenManager.saveMid(getApplication(), navData.mid)
        com.android.purebilibili.core.network.AccountCookieStore.update(
            getApplication(), com.android.purebilibili.core.network.AccountCookieSession.main(),
            mapOf("DedeUserID" to navData.mid.toString()),
        )
        TokenManager.saveVipStatus(navData.vip.status == 1)
        AccountSessionStore.upsertCurrentAccount(getApplication(), navData)
    }
    
    /**
     * 重置手机登录状态
     */
    fun resetPhoneLogin() {
        clearCaptchaChallenge()
        currentCaptchaKey = ""
        currentPhone = ""
        currentCountryCode = DEFAULT_PHONE_REGION_CID
        clearRiskSession()
        _state.value = LoginState.PhoneIdle
    }

    private companion object {
        const val CAPTCHA_RETRY_CODE = -105
    }
    
    // ==========  TV 端登录方法 (获取 access_token 用于高画质视频) ==========
    
    private var tvAuthCode: String = ""
    private var isTvPolling = false
    
    /**
     * 使用 TV 端 API 获取二维码 (获取 access_token)
     * 这个方法返回的 access_token 可用于获取 4K/HDR/1080P60 高画质视频
     */
    fun loadTvQrCode() {
        isTvPolling = true
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                Logger.d("TvLogin", "1. 开始获取 TV 二维码...")
                
                val response = com.android.purebilibili.data.repository.QrLoginRepository.generate()

                if (response.code == 0 && response.data != null) {
                    val checkedResponseData = requireNotNull(response.data)
                    val data = checkedResponseData
                    tvAuthCode = data.authCode ?: throw Exception("TV auth_code 为空")
                    val qrUrl = data.url ?: throw Exception("TV 二维码 URL 为空")
                    
                    Logger.d("TvLogin", "TV 二维码获取成功")
                    
                    val bitmap = generateQrBitmap(qrUrl)
                    currentBitmap = bitmap
                    _state.value = LoginState.QrCode(bitmap)
                    
                    startTvPolling()
                } else {
                    Logger.d("TvLogin", "获取 TV 二维码失败: code=${response.code}, msg=${response.message}")
                    _state.value = LoginState.Error("获取二维码失败: ${response.message}")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("TvLogin", "获取 TV 二维码异常", e)
                _state.value = LoginState.Error(e.message ?: "网络错误")
            }
        }
    }
    
    /**
     * 轮询 TV 登录状态
     */
    private fun startTvPolling() {
        webPollJob?.cancel()
        tvPollJob?.cancel()
        tvPollJob = viewModelScope.launch {
            Logger.d("TvLogin", "3. 开始 TV 轮询...")
            while (isTvPolling) {
                delay(2000)
                try {
                    val response = com.android.purebilibili.data.repository.QrLoginRepository.poll(tvAuthCode)

                    Logger.d("TvLogin", "TV 轮询状态: code=${response.code}")
                    
                    when (response.code) {
                        0 -> {
                            // 登录成功
                            Logger.d("TvLogin", " TV 登录成功!")
                            val data = response.data
                            if (data != null) {
                                val cookies = data.cookieInfo?.cookies.orEmpty()
                                    .associate { it.name to it.value }
                                val sessData = cookies["SESSDATA"].orEmpty()
                                if (sessData.isBlank()) {
                                    _state.value = LoginState.Error("登录数据缺少 SESSDATA")
                                    isTvPolling = false
                                    return@launch
                                }
                                isTvPolling = false
                                completeLogin(
                                    sessData = sessData,
                                    csrf = cookies["bili_jct"].orEmpty(),
                                    buvid3 = cookies["buvid3"].orEmpty(),
                                    mid = data.mid,
                                    accessToken = data.accessToken,
                                    refreshToken = data.refreshToken,
                                    source = "qrcode_tv",
                                    accessTokenPlatform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD,
                                    cookies = cookies,
                                )
                            } else {
                                _state.value = LoginState.Error("登录数据解析失败")
                            }
                        }
                        86039 -> {
                            // 尚未确认
                            Logger.d("TvLogin", "等待扫码确认...")
                        }
                        86090 -> {
                            // 已扫码待确认
                            Logger.d("TvLogin", " 二维码已扫描，等待确认...")
                            currentBitmap?.let { bitmap ->
                                withContext(Dispatchers.Main.immediate) {
                                    _state.value = LoginState.Scanned(bitmap)
                                }
                            }
                        }
                        86038 -> {
                            // 二维码过期
                            _state.value = LoginState.Error("二维码已过期，请刷新")
                            isTvPolling = false
                        }
                        else -> {
                            Logger.d("TvLogin", "未知状态: ${response.code} - ${response.message}")
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    if (!isTvPolling) _state.value = LoginState.Error(e.message ?: "登录态校验失败")
                    com.android.purebilibili.core.util.Logger.e("TvLogin", "TV 轮询异常", e)
                }
            }
        }
    }
    
    /**
     * 停止 TV 轮询
     */
    fun stopTvPolling() {
        isTvPolling = false
        tvPollJob?.cancel()
        tvPollJob = null
    }

}
