package com.android.purebilibili.core.ui.adaptive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级设备散热与省电档位：
 * - [NORMAL]：常规散热与供电，完全释放硬件性能与光学特效；
 * - [ELEVATED]：温热 / 即将逼近热墙 / 系统省电模式，提前收紧高带宽着色器与超分档位；
 * - [CRITICAL]：严重发热或已撞热墙，同步收紧动效档位并把重 CNN 超分降为轻量管线。
 */
enum class DeviceThermalTier {
    NORMAL,
    ELEVATED,
    CRITICAL,
}

const val THERMAL_HEADROOM_ELEVATED_THRESHOLD = 0.88f
const val THERMAL_HEADROOM_CRITICAL_THRESHOLD = 1.0f
private const val THERMAL_STATUS_MODERATE_CODE = 2
private const val THERMAL_STATUS_SEVERE_CODE = 3

/**
 * 温控预测值（`getThermalHeadroom`）对应的降档阈值：
 * - Android 15+ 优先采用 `PowerManager.getThermalHeadroomThresholds()` 返回的设备校准值；
 * - 低版本或平台暂不可用时回退至保守默认值。
 */
data class ThermalHeadroomThresholds(
    val elevated: Float = THERMAL_HEADROOM_ELEVATED_THRESHOLD,
    val critical: Float = THERMAL_HEADROOM_CRITICAL_THRESHOLD,
)

fun resolveThermalHeadroomThresholds(
    statusThresholds: Map<Int, Float>,
): ThermalHeadroomThresholds {
    val rawCritical = statusThresholds[THERMAL_STATUS_SEVERE_CODE]
        ?.takeIf { it.isFinite() && it > 0f }
        ?: THERMAL_HEADROOM_CRITICAL_THRESHOLD
    val rawElevated = statusThresholds[THERMAL_STATUS_MODERATE_CODE]
        ?.takeIf { it.isFinite() && it > 0f }
        ?: THERMAL_HEADROOM_ELEVATED_THRESHOLD
    return ThermalHeadroomThresholds(
        elevated = minOf(rawElevated, rawCritical),
        critical = rawCritical,
    )
}

/**
 * 根据 `PowerManager.currentThermalStatus`、`getThermalHeadroom` 预测值与省电模式解析设备档位。
 * 数值沿用 Android `PowerManager.THERMAL_STATUS_*` 常量（2 = MODERATE，3 = SEVERE）。
 */
fun resolveDeviceThermalTier(
    thermalStatus: Int,
    thermalHeadroom: Float = Float.NaN,
    powerSaveMode: Boolean = false,
    headroomThresholds: ThermalHeadroomThresholds = ThermalHeadroomThresholds(),
): DeviceThermalTier {
    val headroomCritical = thermalHeadroom.isFinite() && thermalHeadroom >= headroomThresholds.critical
    if (thermalStatus >= THERMAL_STATUS_SEVERE_CODE || headroomCritical) {
        return DeviceThermalTier.CRITICAL
    }
    val headroomElevated = thermalHeadroom.isFinite() && thermalHeadroom >= headroomThresholds.elevated
    if (powerSaveMode || thermalStatus >= THERMAL_STATUS_MODERATE_CODE || headroomElevated) {
        return DeviceThermalTier.ELEVATED
    }
    return DeviceThermalTier.NORMAL
}

/**
 * 计算温控预测值需要补采的延迟毫秒数：
 * - 若本次回调命中节流窗口（[throttledRemainingMs] > 0），在窗口结束后补采最新预测值；
 * - 只要预测值额外抬高了最终温控档位（包括 `NORMAL -> ELEVATED/CRITICAL` 及 `ELEVATED -> CRITICAL`），
 *   就在预测窗口（[forecastWindowMs]）到期后复检一次，避免无新系统状态回调时永久卡在更高热档。
 */
fun resolveThermalHeadroomFollowUpDelayMs(
    thermalStatus: Int,
    thermalHeadroom: Float,
    powerSaveMode: Boolean,
    throttledRemainingMs: Long,
    forecastWindowMs: Long,
    headroomThresholds: ThermalHeadroomThresholds = ThermalHeadroomThresholds(),
): Long? {
    if (throttledRemainingMs > 0L) {
        return throttledRemainingMs
    }
    if (forecastWindowMs <= 0L || !thermalHeadroom.isFinite()) {
        return null
    }
    val baseTier = resolveDeviceThermalTier(
        thermalStatus = thermalStatus,
        thermalHeadroom = Float.NaN,
        powerSaveMode = powerSaveMode,
        headroomThresholds = headroomThresholds,
    )
    val effectiveTier = resolveDeviceThermalTier(
        thermalStatus = thermalStatus,
        thermalHeadroom = thermalHeadroom,
        powerSaveMode = powerSaveMode,
        headroomThresholds = headroomThresholds,
    )
    return if (effectiveTier.ordinal > baseTier.ordinal) forecastWindowMs else null
}

/**
 * 将主动热/省电预算叠加到 JankStats 视觉守卫决策上，不污染掉帧惩罚历史。
 */
fun applyThermalTierToVisualGuardDecision(
    jankDecision: RuntimeVisualGuardDecision,
    thermalTier: DeviceThermalTier,
    enabled: Boolean,
): RuntimeVisualGuardDecision {
    if (!enabled || thermalTier == DeviceThermalTier.NORMAL) return jankDecision
    return when (thermalTier) {
        DeviceThermalTier.NORMAL -> jankDecision
        DeviceThermalTier.ELEVATED -> jankDecision.copy(
            forceLowBlurBudget = true,
        )
        DeviceThermalTier.CRITICAL -> jankDecision.copy(
            effectiveMotionTier = minMotionTier(jankDecision.effectiveMotionTier, MotionTier.Reduced),
            forceLowBlurBudget = true,
            downgraded = true,
        )
    }
}

/**
 * 进程级只读快照 + 状态流：
 * - [tier] 供 GL 线程与 AGSL 绘制阶段无锁读取；
 * - [tierFlow] 供组合根与长生命周期观察者订阅。
 */
object DeviceThermalBudgetState {
    @Volatile
    var tier: DeviceThermalTier = DeviceThermalTier.NORMAL
        private set

    private val _tierFlow = MutableStateFlow(DeviceThermalTier.NORMAL)
    val tierFlow: StateFlow<DeviceThermalTier> = _tierFlow.asStateFlow()

    fun update(newTier: DeviceThermalTier) {
        if (tier == newTier && _tierFlow.value == newTier) return
        tier = newTier
        _tierFlow.value = newTier
    }
}
