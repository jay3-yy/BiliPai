package com.android.purebilibili.feature.home.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

internal const val GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES = 3
private const val HighlightSectorCount = 360 / GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES
private const val UpwardHighlightSector = 270 / GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES
private const val MinimumProjectedGravitySquared = 0.01f
private val HighlightSectorRadians = (2 * PI / HighlightSectorCount).toFloat()

internal fun quantizeGravityHighlightSector(gravityX: Float, gravityY: Float): Int {
    if (gravityX * gravityX + gravityY * gravityY <= MinimumProjectedGravitySquared) {
        return UpwardHighlightSector
    }
    val sector = (atan2(gravityY, gravityX) / HighlightSectorRadians).roundToInt()
    return Math.floorMod(sector, HighlightSectorCount)
}

internal interface GlassGravitySensorBackend {
    fun start(onSample: (Float, Float) -> Unit): Boolean
    fun stop()
}

/** 活跃高光共享一个订阅；最后一个使用者退出后停止接收，并丢弃已排队的回调。 */
internal class SharedGlassGravitySensor(private val backend: GlassGravitySensorBackend) {
    private val consumers = mutableSetOf<Any>()
    private val highlightSector = mutableIntStateOf(UpwardHighlightSector)
    val state: State<Int> get() = highlightSector
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var listening = false
    private var initialized = false
    private var generation = 0L

    fun acquire(token: Any) {
        if (!consumers.add(token) || consumers.size != 1) return
        initialized = false
        val session = ++generation
        listening = true
        listening = backend.start { x, y ->
            if (session == generation && listening && consumers.isNotEmpty()) {
                if (!initialized) {
                    initialized = true
                    smoothedX = x
                    smoothedY = y
                } else {
                    smoothedX += (x - smoothedX) * 0.15f
                    smoothedY += (y - smoothedY) * 0.15f
                }
                // 保留原始采样和平滑，只在可见的高光方向变化时通知绘制层。
                val nextSector = quantizeGravityHighlightSector(smoothedX, smoothedY)
                if (nextSector != highlightSector.intValue) {
                    highlightSector.intValue = nextSector
                }
            }
        }
    }

    fun release(token: Any) {
        if (!consumers.remove(token) || consumers.isNotEmpty()) return
        listening = false
        generation++
        backend.stop()
        initialized = false
    }
}

private class AndroidGlassGravitySensorBackend(context: Context) : GlassGravitySensorBackend {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var listener: SensorEventListener? = null

    override fun start(onSample: (Float, Float) -> Unit): Boolean {
        val manager = manager ?: return false
        val sensor = sensor ?: return false
        val matrix = FloatArray(9)
        val next = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                onSample(-matrix[6], -matrix[7])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = next
        return try {
            manager.registerListener(next, sensor, SensorManager.SENSOR_DELAY_GAME)
        } catch (_: SecurityException) {
            listener = null
            false
        }
    }

    override fun stop() {
        listener?.let { manager?.unregisterListener(it) }
        listener = null
    }
}

// 仅保存 Application 的服务句柄，不持有 Activity、窗口或组件。
private val glassGravitySensors = mutableMapOf<Context, SharedGlassGravitySensor>()

@Composable
internal fun rememberSharedGlassHighlightSector(active: Boolean): State<Int> {
    val context = LocalContext.current.applicationContext
    val sensor = remember(context) {
        glassGravitySensors.getOrPut(context) {
            SharedGlassGravitySensor(AndroidGlassGravitySensorBackend(context))
        }
    }
    val token = remember { Any() }
    if (active) {
        DisposableEffect(sensor, token) {
            sensor.acquire(token)
            onDispose { sensor.release(token) }
        }
    }
    // 隐藏高光保持最后方向，且不再观察其他可见组件推动的传感器状态。
    return remember(sensor, active) {
        if (active) sensor.state else mutableIntStateOf(Snapshot.withoutReadObservation { sensor.state.value })
    }
}
