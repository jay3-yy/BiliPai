package com.android.purebilibili.feature.login

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface QrAuthorizationState {
    data object Idle : QrAuthorizationState
    data object LoginRequired : QrAuthorizationState
    data object Checking : QrAuthorizationState
    data class Ready(
        val type: BilibiliLoginQrType, val accountName: String, val mid: Long,
        val location: String? = null, val locationDiffers: Boolean = false,
    ) : QrAuthorizationState
    data object Authorizing : QrAuthorizationState
    data class Authorized(val type: BilibiliLoginQrType) : QrAuthorizationState
    data class Failed(val message: String) : QrAuthorizationState
}

/** Independent from LoginState: authorizing another device never logs in this device. */
internal class OfficialQrAuthorizationViewModel : ViewModel() {
    private val _state = MutableStateFlow<QrAuthorizationState>(initialState())
    val state = _state.asStateFlow()
    private var requestJob: Job? = null
    private var prepared: PreparedQrAuthorization? = null
    private val service by lazy { OfficialQrAuthorizationService(NetworkModule.qrAuthorizationApi, ::readSession) }

    @MainThread
    fun scan(raw: String) {
        if (_state.value != QrAuthorizationState.Idle) return
        val qr = parseBilibiliLoginQr(raw)
        if (qr == null) {
            _state.value = QrAuthorizationState.Failed("请扫描 B 站网页或 TV 登录二维码")
            return
        }
        // Change state before launching, so repeated camera frames cannot enqueue requests.
        _state.value = QrAuthorizationState.Checking
        requestJob = viewModelScope.launch {
            try {
                val result = service.prepare(qr)
                ensureActive()
                prepared = result
                _state.value = QrAuthorizationState.Ready(qr.type, result.accountName, result.mid, result.location, result.locationDiffers)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                ensureActive()
                _state.value = QrAuthorizationState.Failed(error.message ?: "请先登录本应用")
            } catch (error: QrAuthorizationException) {
                ensureActive()
                _state.value = QrAuthorizationState.Failed(error.message.orEmpty())
            } catch (_: Exception) {
                ensureActive()
                _state.value = QrAuthorizationState.Failed("登录请求校验失败，请检查网络后重新扫描")
            }
        }
    }

    @MainThread
    fun confirm() {
        if (_state.value !is QrAuthorizationState.Ready) return
        val request = prepared ?: return
        prepared = null
        _state.value = QrAuthorizationState.Authorizing
        requestJob = viewModelScope.launch {
            try {
                service.confirm(request)
                ensureActive()
                _state.value = QrAuthorizationState.Authorized(request.qr.type)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: QrAuthorizationException) {
                ensureActive()
                _state.value = QrAuthorizationState.Failed(error.message.orEmpty())
            } catch (_: Exception) {
                ensureActive()
                _state.value = QrAuthorizationState.Failed(
                    "未能确认授权结果，请先查看对方设备是否已登录；未登录时刷新二维码后重新扫描"
                )
            }
        }
    }

    @MainThread
    fun scannerFailed(message: String) {
        _state.update { current ->
            if (current == QrAuthorizationState.Idle) QrAuthorizationState.Failed(message) else current
        }
    }

    @MainThread
    fun reset() {
        requestJob?.cancel()
        requestJob = null
        prepared = null
        _state.value = initialState()
    }

    private fun initialState(): QrAuthorizationState =
        if (TokenManager.sessDataCache.isNullOrBlank() || TokenManager.csrfCache.isNullOrBlank())
            QrAuthorizationState.LoginRequired else QrAuthorizationState.Idle

    private fun readSession() = QrAuthorizationSession(
        TokenManager.sessDataCache.orEmpty(), TokenManager.csrfCache.orEmpty(),
        TokenManager.midCache, TokenManager.buvid3Cache.orEmpty(),
        TokenManager.accessTokenCache.orEmpty(), TokenManager.accessTokenPlatformCache,
    )
}
