package com.android.purebilibili.core.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * 面向线性马达的高阶语义触感模式。
 * 支持 Android 11+ VibrationEffect.Composition 的硬件直接编排原生原语，
 * 不支持的设备按对应 View.performHapticFeedback 常量无缝回退。
 */
enum class AppRichHapticPattern {
    /** 进度条或精细手势的轻刻度 */
    SEEK_TICK,
    /** 章节吸附、高能节点或进入精细拖动模式的明显刻度 */
    SEEK_SNAP,
    /** 长按一键三连蓄力三阶渐强脉冲 */
    TRIPLE_CHARGE_LOW,
    TRIPLE_CHARGE_MID,
    TRIPLE_CHARGE_HIGH,
    /** 一键三连完成瞬间的复合反馈 */
    TRIPLE_COMPLETE,
    /** 液态玻璃底栏近距吸附或设备接力卡片甩出的阻尼反馈 */
    GLASS_SNAP,
    /** 长按倍速锁定双脉冲 */
    SPEED_LOCK,
}

data class RichHapticStep(
    val primitiveId: Int,
    val scale: Float,
    val delayMs: Int = 0,
)

/** 纯策略解析：根据当前设备支持的原语集合选出最优微波形编排；返回空列表表示回退到 View 触感常量。 */
fun resolveRichHapticSteps(
    pattern: AppRichHapticPattern,
    supportedPrimitives: Set<Int>,
): List<RichHapticStep> {
    if (supportedPrimitives.isEmpty()) return emptyList()

    fun pickTickPrimitive(): Int? = when {
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK in supportedPrimitives ->
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        VibrationEffect.Composition.PRIMITIVE_TICK in supportedPrimitives ->
            VibrationEffect.Composition.PRIMITIVE_TICK
        else -> null
    }

    val steps = when (pattern) {
        AppRichHapticPattern.SEEK_TICK -> {
            val primitive = pickTickPrimitive() ?: return emptyList()
            val scale = if (primitive == VibrationEffect.Composition.PRIMITIVE_LOW_TICK) 0.34f else 0.26f
            listOf(RichHapticStep(primitiveId = primitive, scale = scale))
        }
        AppRichHapticPattern.SEEK_SNAP -> {
            val primitive = when {
                VibrationEffect.Composition.PRIMITIVE_CLICK in supportedPrimitives ->
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                VibrationEffect.Composition.PRIMITIVE_TICK in supportedPrimitives ->
                    VibrationEffect.Composition.PRIMITIVE_TICK
                else -> return emptyList()
            }
            listOf(RichHapticStep(primitiveId = primitive, scale = 0.78f))
        }
        AppRichHapticPattern.TRIPLE_CHARGE_LOW -> {
            val primitive = pickTickPrimitive() ?: return emptyList()
            listOf(RichHapticStep(primitiveId = primitive, scale = 0.30f))
        }
        AppRichHapticPattern.TRIPLE_CHARGE_MID -> {
            val primitive = pickTickPrimitive() ?: return emptyList()
            listOf(RichHapticStep(primitiveId = primitive, scale = 0.56f))
        }
        AppRichHapticPattern.TRIPLE_CHARGE_HIGH -> {
            val primitive = when {
                VibrationEffect.Composition.PRIMITIVE_TICK in supportedPrimitives ->
                    VibrationEffect.Composition.PRIMITIVE_TICK
                VibrationEffect.Composition.PRIMITIVE_CLICK in supportedPrimitives ->
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                else -> return emptyList()
            }
            listOf(RichHapticStep(primitiveId = primitive, scale = 0.84f))
        }
        AppRichHapticPattern.TRIPLE_COMPLETE -> {
            when {
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE in supportedPrimitives &&
                    VibrationEffect.Composition.PRIMITIVE_THUD in supportedPrimitives -> listOf(
                    RichHapticStep(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale = 0.85f),
                    RichHapticStep(VibrationEffect.Composition.PRIMITIVE_THUD, scale = 0.95f, delayMs = 18),
                )
                VibrationEffect.Composition.PRIMITIVE_CLICK in supportedPrimitives -> listOf(
                    RichHapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, scale = 0.95f),
                )
                else -> return emptyList()
            }
        }
        AppRichHapticPattern.GLASS_SNAP -> {
            when {
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL in supportedPrimitives &&
                    VibrationEffect.Composition.PRIMITIVE_THUD in supportedPrimitives -> listOf(
                    RichHapticStep(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, scale = 0.55f),
                    RichHapticStep(VibrationEffect.Composition.PRIMITIVE_THUD, scale = 0.72f, delayMs = 22),
                )
                VibrationEffect.Composition.PRIMITIVE_CLICK in supportedPrimitives -> listOf(
                    RichHapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, scale = 0.72f),
                )
                else -> return emptyList()
            }
        }
        AppRichHapticPattern.SPEED_LOCK -> {
            val primitive = when {
                VibrationEffect.Composition.PRIMITIVE_TICK in supportedPrimitives ->
                    VibrationEffect.Composition.PRIMITIVE_TICK
                VibrationEffect.Composition.PRIMITIVE_CLICK in supportedPrimitives ->
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                else -> return emptyList()
            }
            listOf(
                RichHapticStep(primitiveId = primitive, scale = 0.55f),
                RichHapticStep(primitiveId = primitive, scale = 0.72f, delayMs = 35),
            )
        }
    }
    return steps
}

internal fun resolveRichHapticFallbackConstant(
    pattern: AppRichHapticPattern,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Int = when (pattern) {
    AppRichHapticPattern.SEEK_TICK,
    AppRichHapticPattern.TRIPLE_CHARGE_LOW,
    AppRichHapticPattern.TRIPLE_CHARGE_MID -> {
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    }
    AppRichHapticPattern.SEEK_SNAP,
    AppRichHapticPattern.TRIPLE_CHARGE_HIGH,
    AppRichHapticPattern.SPEED_LOCK -> {
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
    }
    AppRichHapticPattern.TRIPLE_COMPLETE,
    AppRichHapticPattern.GLASS_SNAP -> {
        if (sdkInt >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    }
}

internal object RichHapticEngine {
    private data class Capabilities(
        val vibrator: Vibrator?,
        val supportedPrimitives: Set<Int>,
    )

    @Volatile
    private var cachedCapabilities: Capabilities? = null

    private fun resolveCapabilities(context: Context): Capabilities {
        cachedCapabilities?.let { return it }
        val appContext = context.applicationContext ?: context
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val activeVibrator = vibrator?.takeIf { it.hasVibrator() }
        val supported = if (activeVibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            buildSet {
                val candidates = buildList {
                    add(VibrationEffect.Composition.PRIMITIVE_CLICK)
                    add(VibrationEffect.Composition.PRIMITIVE_TICK)
                    add(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
                    add(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                        add(VibrationEffect.Composition.PRIMITIVE_THUD)
                    }
                }
                val supportFlags = activeVibrator.arePrimitivesSupported(*candidates.toIntArray())
                candidates.forEachIndexed { index, primitive ->
                    if (index < supportFlags.size && supportFlags[index]) {
                        add(primitive)
                    }
                }
            }
        } else {
            emptySet()
        }
        return Capabilities(activeVibrator, supported).also { cachedCapabilities = it }
    }

    fun perform(
        view: View,
        pattern: AppRichHapticPattern,
    ) {
        if (!view.isHapticFeedbackEnabled) return
        val caps = resolveCapabilities(view.context)
        val vibrator = caps.vibrator
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val steps = resolveRichHapticSteps(pattern, caps.supportedPrimitives)
            if (steps.isNotEmpty()) {
                val composition = VibrationEffect.startComposition()
                steps.forEach { step ->
                    composition.addPrimitive(step.primitiveId, step.scale, step.delayMs)
                }
                val effect = composition.compose()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .build()
                    vibrator.vibrate(effect, attrs)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(effect)
                }
                return
            }
        }
        view.performHapticFeedback(resolveRichHapticFallbackConstant(pattern))
    }
}

/**
 * 复用既有 AppHapticScene 位掩码与 AppActionHaptics 去重边界的高阶线性马达触感入口。
 */
@Composable
fun rememberRichHapticFeedback(
    scene: AppHapticScene,
): (AppRichHapticPattern) -> Unit {
    val view = LocalView.current
    val config by rememberUpdatedState(LocalAppThemeConfig.current)
    return remember(view, scene) {
        { pattern ->
            if (config.hapticFeedbackEnabled && config.hapticSettings.isEnabled(scene)) {
                AppActionHaptics.dispatch {
                    RichHapticEngine.perform(view = view, pattern = pattern)
                }
            } else {
                AppActionHaptics.suppressCurrentActionFeedback()
            }
        }
    }
}
