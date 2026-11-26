package com.android.purebilibili.feature.audio.ambient

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

internal const val AMBIENT_NOISE_SAMPLE_RATE_HZ = 22_050
internal const val AMBIENT_NOISE_MIN_VOLUME = 0.05f
internal const val AMBIENT_NOISE_MAX_VOLUME = 1.0f
internal const val AMBIENT_NOISE_DEFAULT_VOLUME = 0.35f

enum class AmbientNoisePreset(
    val label: String,
    val description: String,
) {
    NONE("关闭", "仅播放当前音频"),
    RAIN("细雨", "温柔绵密的窗外雨声"),
    FIREPLACE("壁炉", "暖意柴火低频与轻脆噼啪"),
    TRAIN("列车", "平稳车厢底噪与铁轨节律"),
}

data class AmbientNoiseUiState(
    val preset: AmbientNoisePreset = AmbientNoisePreset.NONE,
    val volume: Float = AMBIENT_NOISE_DEFAULT_VOLUME,
    val isPlaying: Boolean = false,
)

internal fun normalizeAmbientNoiseVolume(volume: Float): Float =
    volume.coerceIn(AMBIENT_NOISE_MIN_VOLUME, AMBIENT_NOISE_MAX_VOLUME)

/**
 * 零音频资源体积的纯本地实时 PCM 氛围音合成器（运行在后台计算线程）。
 * 每种预设均通过温和低通/节律调制保持听感柔和，不产生尖锐刺耳高频。
 */
internal class AmbientNoiseSynthesizer(
    seed: Int = 0x5EED1234,
) {
    private var rngState: Int = if (seed == 0) 0x13579BDF else seed
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var lowPass = 0.0
    private var crackleEnv = 0.0
    private var sampleCursor = 0L

    fun reset() {
        b0 = 0.0
        b1 = 0.0
        b2 = 0.0
        lowPass = 0.0
        crackleEnv = 0.0
        sampleCursor = 0L
    }

    fun fillBuffer(preset: AmbientNoisePreset, output: ShortArray) {
        if (preset == AmbientNoisePreset.NONE) {
            output.fill(0)
            return
        }
        val sr = AMBIENT_NOISE_SAMPLE_RATE_HZ.toDouble()
        for (i in output.indices) {
            val white = nextSignedUnit()
            val sample = when (preset) {
                AmbientNoisePreset.NONE -> 0.0
                AmbientNoisePreset.RAIN -> synthesizeRainSample(white, sampleCursor / sr)
                AmbientNoisePreset.FIREPLACE -> synthesizeFireplaceSample(white, sampleCursor / sr)
                AmbientNoisePreset.TRAIN -> synthesizeTrainSample(white, sampleCursor / sr)
            }
            sampleCursor++
            val softLimited = tanh(sample * 1.15)
            output[i] = (softLimited * Short.MAX_VALUE * 0.72).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun synthesizeRainSample(white: Double, timeSec: Double): Double {
        // 轻量粉红噪声近似 + 缓慢呼吸起伏，模拟柔和雨幕
        b0 = 0.997 * b0 + white * 0.029
        b1 = 0.985 * b1 + white * 0.032
        b2 = 0.950 * b2 + white * 0.048
        val pink = b0 + b1 + b2 + white * 0.018
        lowPass = lowPass * 0.78 + pink * 0.22
        val breeze = 0.85 + 0.15 * sin(2.0 * PI * 0.11 * timeSec)
        return lowPass * breeze * 1.35
    }

    private fun synthesizeFireplaceSample(white: Double, timeSec: Double): Double {
        // 温暖低频炉膛底噪 + 稀疏轻柔柴火噼啪
        lowPass = lowPass * 0.965 + white * 0.035
        val rumble = lowPass * (2.6 + 0.35 * sin(2.0 * PI * 0.27 * timeSec))
        if (crackleEnv < 0.001 && nextUnit() < 0.00045) {
            crackleEnv = 0.35 + nextUnit() * 0.45
        }
        val crackle = if (crackleEnv > 0.001) {
            crackleEnv *= 0.962
            white * crackleEnv * 0.55
        } else {
            0.0
        }
        return rumble + crackle
    }

    private fun synthesizeTrainSample(white: Double, timeSec: Double): Double {
        // 车厢低频底噪 + 每 ~1.85 秒一组轻微双节拍铁轨接缝声（da-dum）
        lowPass = lowPass * 0.94 + white * 0.06
        val phase = timeSec % 1.85
        val pulseA = gaussianPulse(phase, center = 0.18, width = 0.028)
        val pulseB = gaussianPulse(phase, center = 0.34, width = 0.028) * 0.82
        val railThump = (pulseA + pulseB) * (0.28 + 0.35 * lowPass)
        val cabinHum = lowPass * 2.1 + 0.045 * sin(2.0 * PI * 68.0 * timeSec)
        return cabinHum + railThump
    }

    private fun gaussianPulse(x: Double, center: Double, width: Double): Double {
        val d = (x - center) / width
        return if (d * d > 12.0) 0.0 else kotlin.math.exp(-0.5 * d * d)
    }

    private fun nextUnit(): Double {
        var x = rngState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rngState = x
        return ((x ushr 1) / Int.MAX_VALUE.toDouble())
    }

    private fun nextSignedUnit(): Double = nextUnit() * 2.0 - 1.0
}
