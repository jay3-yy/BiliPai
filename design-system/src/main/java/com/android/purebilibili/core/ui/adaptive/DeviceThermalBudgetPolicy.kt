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

/**
 * 根据 `PowerManager.currentThermalStatus`、`getThermalHeadroom` 预测值与省电模式解析设备档位。
 * 数值沿用 Android `PowerManager.THERMAL_STATUS_*` 常量（2 = MODERATE，3 = SEVERE）。
 */
fun resolveDeviceThermalTier(
    thermalStatus: Int,
    thermalHeadroom: Float = Float.NaN,
    powerSaveMode: Boolean = false,
): DeviceThermalTier {
    val headroomCritical = thermalHeadroom.isFinite() && thermalHeadroom >= THERMAL_HEADROOM_CRITICAL_THRESHOLD
    if (thermalStatus >= 3 || headroomCritical) {
        return DeviceThermalTier.CRITICAL
    }
    val headroomElevated = thermalHeadroom.isFinite() && thermalHeadroom >= THERMAL_HEADROOM_ELEVATED_THRESHOLD
    if (powerSaveMode || thermalStatus >= 2 || headroomElevated) {
        return DeviceThermalTier.ELEVATED
    }
    return DeviceThermalTier.NORMAL
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
