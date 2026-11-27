package com.android.purebilibili.core.ui.performance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.android.purebilibili.core.coroutines.AppScope
import com.android.purebilibili.core.player.PlaybackHttpDataSourceFactory
import com.android.purebilibili.core.ui.adaptive.DeviceThermalBudgetState
import com.android.purebilibili.core.ui.adaptive.resolveDeviceThermalTier
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 进程级散热与省电模式监听中枢：
 * - 事件驱动（`OnThermalStatusChangedListener` + `ACTION_POWER_SAVE_MODE_CHANGED`），空闲零轮询；
 * - 所有 `PowerManager` Binder 查询（`currentThermalStatus`、`getThermalHeadroom(10)`、`isPowerSaveMode`）
 *   严格在 `AppScope.ioScope` 后台串行执行，不阻塞主线程，并在写入 `AppRuntimeVisualGuardTracker` 时切回主线程。
 */
internal object DeviceThermalMonitor {
    private const val HEADROOM_FORECAST_SECONDS = 10
    private const val MIN_HEADROOM_QUERY_INTERVAL_MS = 10_000L

    private val started = AtomicBoolean(false)
    private val refreshMutex = Mutex()
    @Volatile
    private var lastHeadroomQueryMs: Long = 0L
    @Volatile
    private var cachedHeadroom: Float = Float.NaN

    fun ensureStarted(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        AppScope.ioScope.launch {
            PlaybackHttpDataSourceFactory.warmUpAsync(appContext)
        }
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        val refreshAsync = {
            AppScope.ioScope.launch {
                refreshMutex.withLock {
                    val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        powerManager.currentThermalStatus
                    } else {
                        0
                    }
                    val headroom = queryThermalHeadroomIfAllowed(powerManager)
                    val powerSaveMode = powerManager.isPowerSaveMode
                    val tier = resolveDeviceThermalTier(
                        thermalStatus = thermalStatus,
                        thermalHeadroom = headroom,
                        powerSaveMode = powerSaveMode,
                    )
                    DeviceThermalBudgetState.update(tier)
                    withContext(Dispatchers.Main.immediate) {
                        AppRuntimeVisualGuardTracker.setThermalTier(tier)
                    }
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshAsync()
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener(
                appContext.mainExecutor,
                PowerManager.OnThermalStatusChangedListener { refreshAsync() },
            )
        }
        refreshAsync()
    }

    private fun queryThermalHeadroomIfAllowed(powerManager: PowerManager): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Float.NaN
        val now = SystemClock.elapsedRealtime()
        if (lastHeadroomQueryMs > 0L && now - lastHeadroomQueryMs < MIN_HEADROOM_QUERY_INTERVAL_MS) {
            return cachedHeadroom
        }
        lastHeadroomQueryMs = now
        val value = runCatching { powerManager.getThermalHeadroom(HEADROOM_FORECAST_SECONDS) }
            .getOrDefault(Float.NaN)
        if (value.isFinite()) {
            cachedHeadroom = value
        }
        return cachedHeadroom
    }
}
