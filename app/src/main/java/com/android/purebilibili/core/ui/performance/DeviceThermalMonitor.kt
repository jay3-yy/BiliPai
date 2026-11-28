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
import com.android.purebilibili.core.ui.adaptive.ThermalHeadroomThresholds
import com.android.purebilibili.core.ui.adaptive.resolveDeviceThermalTier
import com.android.purebilibili.core.ui.adaptive.resolveThermalHeadroomFollowUpDelayMs
import com.android.purebilibili.core.ui.adaptive.resolveThermalHeadroomThresholds
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val deferredLock = Any()
    private var deferredRefreshJob: Job? = null
    @Volatile
    private var lastHeadroomQueryMs: Long = 0L
    @Volatile
    private var cachedHeadroom: Float = Float.NaN
    @Volatile
    private var cachedThresholds: ThermalHeadroomThresholds? = null
    @Volatile
    private var thresholdsUnsupported: Boolean = false

    fun ensureStarted(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        AppScope.ioScope.launch {
            PlaybackHttpDataSourceFactory.warmUpAsync(appContext)
        }
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        lateinit var refreshAsync: () -> Unit
        fun scheduleDeferredRefresh(delayMs: Long?) {
            synchronized(deferredLock) {
                deferredRefreshJob?.cancel()
                deferredRefreshJob = if (delayMs != null && delayMs > 0L) {
                    AppScope.ioScope.launch {
                        delay(delayMs)
                        refreshAsync()
                    }
                } else {
                    null
                }
            }
        }

        refreshAsync = {
            AppScope.ioScope.launch {
                refreshMutex.withLock {
                    val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        powerManager.currentThermalStatus
                    } else {
                        0
                    }
                    val (headroom, throttledRemainingMs) = queryThermalHeadroomIfAllowed(powerManager)
                    val headroomThresholds = queryThermalHeadroomThresholdsIfNeeded(powerManager)
                    val powerSaveMode = powerManager.isPowerSaveMode
                    val tier = resolveDeviceThermalTier(
                        thermalStatus = thermalStatus,
                        thermalHeadroom = headroom,
                        powerSaveMode = powerSaveMode,
                        headroomThresholds = headroomThresholds,
                    )
                    DeviceThermalBudgetState.update(tier)
                    withContext(Dispatchers.Main.immediate) {
                        AppRuntimeVisualGuardTracker.setThermalTier(tier)
                    }
                    val followUpDelayMs = resolveThermalHeadroomFollowUpDelayMs(
                        thermalStatus = thermalStatus,
                        thermalHeadroom = headroom,
                        powerSaveMode = powerSaveMode,
                        throttledRemainingMs = throttledRemainingMs,
                        forecastWindowMs = MIN_HEADROOM_QUERY_INTERVAL_MS,
                        headroomThresholds = headroomThresholds,
                    )
                    scheduleDeferredRefresh(followUpDelayMs)
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

    private fun queryThermalHeadroomIfAllowed(powerManager: PowerManager): Pair<Float, Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Float.NaN to 0L
        val now = SystemClock.elapsedRealtime()
        if (lastHeadroomQueryMs > 0L) {
            val elapsed = now - lastHeadroomQueryMs
            if (elapsed in 0L until MIN_HEADROOM_QUERY_INTERVAL_MS) {
                return cachedHeadroom to (MIN_HEADROOM_QUERY_INTERVAL_MS - elapsed)
            }
        }
        lastHeadroomQueryMs = now
        val value = runCatching { powerManager.getThermalHeadroom(HEADROOM_FORECAST_SECONDS) }
            .getOrDefault(Float.NaN)
        // 超出采样窗口后用本次结果覆盖旧值；查询失败或返回 NaN 时同步失效，不沿用过期高温预测。
        cachedHeadroom = if (value.isFinite()) value else Float.NaN
        return cachedHeadroom to 0L
    }

    private fun queryThermalHeadroomThresholdsIfNeeded(
        powerManager: PowerManager,
    ): ThermalHeadroomThresholds {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || thresholdsUnsupported) {
            return ThermalHeadroomThresholds()
        }
        cachedThresholds?.let { return it }
        return try {
            val map = powerManager.thermalHeadroomThresholds
            if (map.isEmpty()) {
                ThermalHeadroomThresholds()
            } else {
                resolveThermalHeadroomThresholds(map).also { cachedThresholds = it }
            }
        } catch (_: UnsupportedOperationException) {
            thresholdsUnsupported = true
            ThermalHeadroomThresholds()
        } catch (_: IllegalStateException) {
            // 温控服务尚未就绪时暂用默认值，后续温控事件到来时再尝试读取设备校准阈值。
            ThermalHeadroomThresholds()
        }
    }
}
