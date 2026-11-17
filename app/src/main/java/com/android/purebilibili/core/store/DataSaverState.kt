package com.android.purebilibili.core.store

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/** 进程级省流量与默认网络快照：系统查询只用于初始化，后续由设置与网络回调更新。 */
internal class DataSaverState private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("data_saver", Context.MODE_PRIVATE)
    private val connectivity = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
    private val handler = Handler(Looper.getMainLooper())
    private var mode = SettingsManager.getDataSaverModeSync(context)
    private var defaultNetwork = connectivity.activeNetwork
    @Volatile private var capabilities = connectivity.getNetworkCapabilities(defaultNetwork)
    private val cellular: Boolean
        get() = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

    /** 播放器创建只读取当前快照，不在入场组合中重复查询系统服务。 */
    val isOnWifi: Boolean
        get() = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    private val activeState = mutableStateOf(resolveActive())
    val active: State<Boolean> = activeState

    // SharedPreferences 只弱引用监听器；由进程级 holder 保留其强引用。
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "mode" || key == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                refreshMode()
            } else {
                handler.post { refreshMode() }
            }
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNetwork = network
            // 等待紧随其后的 capabilities 再发布，避免先发一次假非蜂窝状态导致图片重载。
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (network == defaultNetwork) {
                this@DataSaverState.capabilities = capabilities
                publish()
            }
        }

        override fun onLost(network: Network) {
            if (network == defaultNetwork) {
                defaultNetwork = null
                capabilities = null
                publish()
            }
        }
    }

    init {
        // 与应用同寿命；所有回调统一在主线程，不为每张图片注册监听器。
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        connectivity.registerDefaultNetworkCallback(networkCallback, handler)
    }

    private fun resolveActive(): Boolean = when (mode) {
        SettingsManager.DataSaverMode.OFF -> false
        SettingsManager.DataSaverMode.ALWAYS -> true
        SettingsManager.DataSaverMode.MOBILE_ONLY -> cellular
    }

    private fun publish() {
        activeState.value = resolveActive()
    }

    private fun refreshMode() {
        mode = SettingsManager.DataSaverMode.fromValue(
            preferences.getInt("mode", SettingsManager.DataSaverMode.MOBILE_ONLY.value)
        )
        publish()
    }

    companion object {
        @Volatile private var instance: DataSaverState? = null

        fun get(context: Context): DataSaverState = instance ?: synchronized(this) {
            instance ?: DataSaverState(context.applicationContext).also { instance = it }
        }
    }
}
