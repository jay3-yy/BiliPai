package com.android.purebilibili.feature.home

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 封面预取需要知道的设备 / 网络约束。
 *
 * 分开成一个不可变值对象，是为了让调用方只取一次、只读一次：
 * 这些判断（`getSystemService`、`getNetworkCapabilities`）都有 IPC 或哈希查找成本，
 * 放进组合期逐帧重算就是典型的"顺手多读一次"，在滚动时会被放大成每帧一次系统调用。
 */
@Immutable
internal data class HomeCoverPreloadDeviceProfile(
    /** 低端机：远场预取被砍掉。 */
    val isLowRamDevice: Boolean,
    /** 计费网络：远场预取被砍掉。 */
    val isMeteredNetwork: Boolean,
)

/** 低于 192MB 堆上限的设备即使在 `isLowRamDevice` 上报 false 时也按低端机处理。 */
private const val HOME_COVER_LOW_RAM_MAX_HEAP_BYTES = 192L * 1024 * 1024

/**
 * 记住一次设备画像，并在**默认网络变化时**刷新计费状态。
 *
 * 为什么不能只 `remember` 住：`isMeteredNetwork` 的单次取值在 WiFi ↔ 蜂窝切换后会立刻过期，
 * 而首页停留时间很长，用户切完网络回来远场预取仍然按旧判断在跑（或仍然被砍掉）。
 * 用 `registerDefaultNetworkCallback` 在 `onCapabilitiesChanged` 里更新是唯一不用轮询的做法，
 * 失败（权限被裁剪 / 系统异常）时静默回退到"组合期取一次"的旧行为。
 */
@Composable
internal fun rememberHomeCoverPreloadDeviceProfile(): HomeCoverPreloadDeviceProfile {
    val context = LocalContext.current
    val isLowRamDevice = remember(context) { resolveHomeCoverLowRamDevice(context) }
    // 兜底：取不到就当作不计费，远场预取最多浪费一点流量，而不是永远不铺。
    var isMeteredNetwork by remember(context) { mutableStateOf(resolveHomeCoverMeteredNetwork(context)) }

    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    isMeteredNetwork =
                        !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }

                override fun onLost(network: Network) {
                    // 旧网络失效时重新解析一次，避免停留在"上一张网络的计费状态"上。
                    isMeteredNetwork = resolveHomeCoverMeteredNetwork(context)
                }
            }
            val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
            onDispose {
                if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
            }
        }
    }

    return remember(isLowRamDevice, isMeteredNetwork) {
        HomeCoverPreloadDeviceProfile(
            isLowRamDevice = isLowRamDevice,
            isMeteredNetwork = isMeteredNetwork,
        )
    }
}

internal fun resolveHomeCoverLowRamDevice(context: Context): Boolean {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    if (activityManager?.isLowRamDevice == true) return true
    // 部分 ROM 的 isLowRamDevice 为 false 但堆上限极小，用堆上限补一刀。
    return Runtime.getRuntime().maxMemory() < HOME_COVER_LOW_RAM_MAX_HEAP_BYTES
}

internal fun resolveHomeCoverMeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
