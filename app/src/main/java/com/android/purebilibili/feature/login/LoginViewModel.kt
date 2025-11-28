package com.android.purebilibili.feature.login

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
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
    object Success : LoginState()
    data class Error(val msg: String) : LoginState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state = _state.asStateFlow()

    private var qrcodeKey: String = ""
    private var isPolling = true

    fun loadQrCode() {
        isPolling = true
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                Log.d("LoginDebug", "1. 开始获取二维码...")

                val resp = NetworkModule.passportApi.generateQrCode()

                // 🔥 核心修复：处理可空类型
                val data = resp.data ?: throw Exception("服务器返回数据为空")
                val url = data.url ?: throw Exception("二维码 URL 为空")

                // 👇 这里使用 ?: 抛出异常，解决了 Type mismatch 问题
                qrcodeKey = data.qrcode_key ?: throw Exception("二维码 Key 为空")

                Log.d("LoginDebug", "2. 二维码获取成功 Key: $qrcodeKey")
                val bitmap = generateQrBitmap(url)
                _state.value = LoginState.QrCode(bitmap)

                startPolling()
            } catch (e: Exception) {
                Log.e("LoginDebug", "获取二维码失败", e)
                _state.value = LoginState.Error(e.message ?: "网络错误")
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            Log.d("LoginDebug", "3. 开始轮询...")
            while (isPolling) {
                delay(3000)
                try {
                    val response = NetworkModule.passportApi.pollQrCode(qrcodeKey)
                    val body = response.body()

                    // 🔥 核心修复：处理可空类型，默认为 -1 防止空指针
                    val code = body?.data?.code ?: -1

                    Log.d("LoginDebug", "轮询状态: Code=$code")

                    if (code == 0) {
                        Log.d("LoginDebug", ">>> 登录成功！开始解析 Cookie <<<")

                        val cookies = response.headers().values("Set-Cookie")
                        var sessData = ""

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
                            if (sessData.isNotEmpty()) break
                        }

                        if (sessData.isNotEmpty()) {
                            Log.d("LoginDebug", "✅ 成功提取 SESSDATA: $sessData")

                            // 保存并更新缓存
                            TokenManager.saveCookies(getApplication(), sessData)

                            isPolling = false
                            withContext(Dispatchers.Main) {
                                _state.value = LoginState.Success
                            }
                        } else {
                            _state.value = LoginState.Error("Cookie 解析失败")
                        }
                    } else if (code == 86038) {
                        _state.value = LoginState.Error("二维码已过期")
                        isPolling = false
                    }
                } catch (e: Exception) {
                    Log.e("LoginDebug", "轮询异常", e)
                }
            }
        }
    }

    fun stopPolling() { isPolling = false }

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
}