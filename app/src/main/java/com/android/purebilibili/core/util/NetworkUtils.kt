// 文件路径: core/util/NetworkUtils.kt
package com.android.purebilibili.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.android.purebilibili.data.model.VideoQuality

/**
 * 网络工具类
 * 
 * 用于检测网络类型，实现网络感知的清晰度默认值
 */
object NetworkUtils {

    internal fun getActiveNetworkCapabilities(context: Context): NetworkCapabilities? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return null
        return manager.getNetworkCapabilities(network)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    
    /**
     * 检查当前是否使用 WiFi 网络
     */
    fun isWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * 检查当前是否使用移动数据
     */
    fun isMobileData(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    
    /**
     * 获取网络感知的默认清晰度 ID
     * 
     * Bilibili 清晰度 ID:
     * - 116: 1080P60
     * - 80: 1080P
     * - 64: 720P
     * - 32: 480P
     * - 16: 360P
     * 
     * @return 根据用户设置和网络类型返回对应清晰度
     */
    fun getDefaultQualityId(context: Context, isOnWifi: Boolean = isWifi(context)): Int {
        val prefs = context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)

        val quality = if (isOnWifi) {
            prefs.getInt("wifi_quality", 80)  // 默认 WiFi=1080P
        } else {
            prefs.getInt("mobile_quality", 64)  // 默认流量=720P
        }
        Logger.d("NetworkUtils", " 获取默认画质: isWifi=$isOnWifi, quality=$quality")
        return quality
    }

    /**
     * 获取当前账号实际可用于首播的默认清晰度。
     *
     * 保留用户的默认画质上限；服务器实际返回的流决定最终画质，
     * 不使用可能滞后的本地会员状态提前截断偏好。
     */
    fun getPlayableDefaultQualityId(
        context: Context,
        isLoggedIn: Boolean,
        isVip: Boolean
    ): Int {
        val autoHighestEnabled = context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getBoolean("auto_highest_quality", false)
        val storedQuality = getDefaultQualityId(context)
        val effectiveQuality = resolvePlaybackDefaultQualityId(
            storedQuality = storedQuality,
            autoHighestEnabled = autoHighestEnabled,
            isLoggedIn = isLoggedIn,
            isVip = isVip
        )
        Logger.d(
            "NetworkUtils",
            " 获取有效默认画质: stored=$storedQuality, autoHighest=$autoHighestEnabled, effective=$effectiveQuality, isLoggedIn=$isLoggedIn, isVip=$isVip"
        )
        return effectiveQuality
    }
    
    /**
     * 获取网络类型描述
     */
    fun getNetworkTypeLabel(context: Context): String {
        return when {
            isWifi(context) -> "WiFi"
            isMobileData(context) -> "移动数据"
            else -> "未连接"
        }
    }
}

internal fun resolvePlaybackDefaultQualityId(
    storedQuality: Int,
    autoHighestEnabled: Boolean,
    isLoggedIn: Boolean,
    isVip: Boolean
): Int {
    if (autoHighestEnabled) {
        return VideoQuality.SUPER_8K.code
    }

    return resolvePlayableDefaultQualityId(
        storedQuality = storedQuality,
        isLoggedIn = isLoggedIn,
        isVip = isVip
    )
}

internal fun shouldRefreshVipStatusBeforeResolvingDefaultQuality(
    storedQuality: Int,
    autoHighestEnabled: Boolean,
    isLoggedIn: Boolean,
    cachedIsVip: Boolean
): Boolean {
    if (!isLoggedIn || cachedIsVip) return false
    return autoHighestEnabled || storedQuality > 80
}

// 保留用户首播画质上限；实际可用轨道决定回退，不提前用会员缓存截断偏好。
@Suppress("UNUSED_PARAMETER")
internal fun resolvePlayableDefaultQualityId(
    storedQuality: Int,
    isLoggedIn: Boolean,
    isVip: Boolean
): Int = storedQuality.takeIf { it > 0 } ?: 64
