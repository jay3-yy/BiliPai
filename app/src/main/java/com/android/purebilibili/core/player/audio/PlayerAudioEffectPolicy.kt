package com.android.purebilibili.core.player.audio

import kotlin.math.log10

/**
 * 原生硬件音频效果预设（API 28+ 由 DynamicsProcessing DSP 管线执行）。
 */
enum class PlayerAudioEffectPreset(
    val storageKey: String,
    val label: String,
    val description: String,
    val bandGainsDb: FloatArray,
    val enableDynamicCompression: Boolean,
    val mbcThresholdDb: Float = -18f,
    val mbcRatio: Float = 1f,
    val mbcPostGainDb: Float = 0f,
    val limiterThresholdDb: Float = -1.5f,
) {
    OFF(
        storageKey = "off",
        label = "原声直出",
        description = "不做动态处理，纯听 1x 倍速时可启用硬件省电卸载",
        bandGainsDb = floatArrayOf(0f, 0f, 0f, 0f, 0f),
        enableDynamicCompression = false,
    ),
    LOUDNESS_NORMALIZE(
        storageKey = "loudness",
        label = "响度均衡",
        description = "平衡不同视频间的音量起伏，连播不突兀",
        bandGainsDb = floatArrayOf(0f, 0.5f, 1.0f, 0.5f, 0f),
        enableDynamicCompression = true,
        mbcThresholdDb = -20f,
        mbcRatio = 3.2f,
        mbcPostGainDb = 3.5f,
        limiterThresholdDb = -1.0f,
    ),
    NIGHT_VOICE(
        storageKey = "night_voice",
        label = "夜间人声",
        description = "压制爆音与低频轰鸣，微提人声清晰度",
        bandGainsDb = floatArrayOf(-3.5f, -1.0f, 2.5f, 3.0f, -1.5f),
        enableDynamicCompression = true,
        mbcThresholdDb = -24f,
        mbcRatio = 4.0f,
        mbcPostGainDb = 4.0f,
        limiterThresholdDb = -2.0f,
    ),
    BASS_CLEAR(
        storageKey = "bass_clear",
        label = "低频通透",
        description = "适度增强低频下潜与高频空气感",
        bandGainsDb = floatArrayOf(3.0f, 1.5f, 0f, 1.5f, 2.5f),
        enableDynamicCompression = false,
        limiterThresholdDb = -1.5f,
    );

    companion object {
        val EQ_CUTOFF_FREQUENCIES_HZ = floatArrayOf(120f, 460f, 1800f, 7000f, 20000f)

        fun fromStorageKey(raw: String?): PlayerAudioEffectPreset =
            entries.firstOrNull { it.storageKey == raw } ?: OFF
    }
}

data class SpatialAudioHardwareStatus(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val available: Boolean = false,
    val headTrackerAvailable: Boolean = false,
) {
    val isActive: Boolean
        get() = supported && enabled && available

    fun formatBadge(): String? = when {
        !isActive -> null
        headTrackerAvailable -> "空间音频 · 头部追踪"
        else -> "空间音频已开启"
    }
}

/** 仅在 1x 恒速、原声直出且未开启额外音量放大时允许音频硬件省电卸载。 */
internal fun shouldEnableBackgroundAudioOffload(
    speed: Float,
    preset: PlayerAudioEffectPreset,
    amplification: Float,
): Boolean {
    if (!speed.isFinite() || !amplification.isFinite()) return false
    return speed in 0.99f..1.01f &&
        preset == PlayerAudioEffectPreset.OFF &&
        amplification <= 1.01f
}

/** 将线性放大倍率（1.0..3.0）换算为 DynamicsProcessing 输入增益 dB。 */
internal fun amplificationToGainDb(amplification: Float): Float {
    val safe = if (amplification.isFinite()) amplification.coerceIn(1f, 3f) else 1f
    if (safe <= 1f) return 0f
    return (20.0 * log10(safe.toDouble())).toFloat()
}
