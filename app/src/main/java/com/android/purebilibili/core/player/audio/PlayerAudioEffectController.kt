package com.android.purebilibili.core.player.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import com.android.purebilibili.feature.video.ui.overlay.playerAmplificationMillibels

internal data class PlayerAudioEffectAvailability(
    val volumeGainAvailable: Boolean,
    val dspPresetAvailable: Boolean,
    val unavailableReason: String? = null,
)

/**
 * 统一音频硬件效果控制器：
 * - API 28+ 使用 DynamicsProcessing（5 段均衡 + MBC 响度压限 + 防削波 Limiter + 输入增益）
 * - API 26–27 或设备禁用 DynamicsProcessing 时退回 LoudnessEnhancer（仅开放音量增益，关闭 DSP 预设）
 */
internal class PlayerAudioEffectController(
    private val onAvailabilityChanged: (PlayerAudioEffectAvailability) -> Unit = {},
) {
    private var boundSessionId: Int = 0
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentPreset: PlayerAudioEffectPreset = PlayerAudioEffectPreset.OFF
    private var currentAmplification: Float = 1f

    val isBound: Boolean
        get() = dynamicsProcessing != null || loudnessEnhancer != null

    fun bindOrUpdate(
        sessionId: Int,
        preset: PlayerAudioEffectPreset,
        amplification: Float,
        forceAttach: Boolean = false,
    ): Boolean {
        currentPreset = preset
        currentAmplification = amplification.coerceIn(1f, 3f)
        val needsEffect = forceAttach ||
            currentPreset != PlayerAudioEffectPreset.OFF ||
            currentAmplification > 1.001f

        if (!needsEffect || sessionId <= 0) {
            release()
            if (sessionId <= 0) {
                onAvailabilityChanged(
                    PlayerAudioEffectAvailability(
                        volumeGainAvailable = false,
                        dspPresetAvailable = false,
                        unavailableReason = "音频会话尚未就绪",
                    )
                )
            }
            return false
        }

        if (boundSessionId == sessionId && isBound) {
            return applyCurrentParameters()
        }

        release()
        boundSessionId = sessionId
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attachDynamicsProcessing(sessionId)
        } else {
            attachLoudnessEnhancer(sessionId)
        }
    }

    private fun attachDynamicsProcessing(sessionId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            val bandCount = PlayerAudioEffectPreset.EQ_CUTOFF_FREQUENCIES_HZ.size
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                CHANNEL_COUNT,
                true,
                bandCount,
                true,
                MBC_BAND_COUNT,
                false,
                0,
                true,
            ).build()
            val effect = DynamicsProcessing(0, sessionId, config)
            try {
                applyToDynamicsProcessing(effect, currentPreset, currentAmplification)
                effect.enabled = true
                check(effect.enabled && effect.hasControl()) { "音频硬件处理不可用" }
                dynamicsProcessing = effect
                effect.setControlStatusListener { changedEffect, granted ->
                    if (dynamicsProcessing !== changedEffect) return@setControlStatusListener
                    val available = granted && runCatching { effect.enabled }.getOrDefault(false)
                    onAvailabilityChanged(
                        PlayerAudioEffectAvailability(
                            volumeGainAvailable = available,
                            dspPresetAvailable = available,
                            unavailableReason = if (available) null else "播放器音频增强控制权已被其他音效占用",
                        )
                    )
                }
            } catch (error: RuntimeException) {
                if (dynamicsProcessing === effect) dynamicsProcessing = null
                runCatching { effect.setControlStatusListener(null) }
                runCatching { effect.release() }
                throw error
            }
            effect
        }.fold(
            onSuccess = { effect ->
                val available = runCatching { effect.enabled && effect.hasControl() }.getOrDefault(false)
                onAvailabilityChanged(
                    PlayerAudioEffectAvailability(
                        volumeGainAvailable = available,
                        dspPresetAvailable = available,
                        unavailableReason = if (available) null else "播放器音频增强控制权已被其他音效占用",
                    )
                )
                available
            },
            onFailure = {
                // 部分定制设备禁用了 DynamicsProcessing，仅退回 LoudnessEnhancer 音量增益并关闭 DSP 预设。
                attachLoudnessEnhancer(sessionId)
            },
        )
    }

    private fun attachLoudnessEnhancer(sessionId: Int): Boolean {
        return runCatching {
            val effect = LoudnessEnhancer(sessionId)
            try {
                effect.setTargetGain(playerAmplificationMillibels(currentAmplification))
                effect.enabled = true
                check(effect.enabled && effect.hasControl()) { "音频增强效果不可用" }
                loudnessEnhancer = effect
                effect.setControlStatusListener { changedEffect: AudioEffect?, granted: Boolean ->
                    if (loudnessEnhancer !== changedEffect) return@setControlStatusListener
                    val available = granted && runCatching { effect.enabled }.getOrDefault(false)
                    onAvailabilityChanged(
                        PlayerAudioEffectAvailability(
                            volumeGainAvailable = available,
                            dspPresetAvailable = false,
                            unavailableReason = if (available) null else "播放器音频增强控制权已被其他音效占用",
                        )
                    )
                }
            } catch (error: RuntimeException) {
                if (loudnessEnhancer === effect) loudnessEnhancer = null
                runCatching { effect.setControlStatusListener(null) }
                runCatching { effect.release() }
                throw error
            }
            effect
        }.fold(
            onSuccess = { effect ->
                val available = runCatching { effect.enabled && effect.hasControl() }.getOrDefault(false)
                onAvailabilityChanged(
                    PlayerAudioEffectAvailability(
                        volumeGainAvailable = available,
                        dspPresetAvailable = false,
                        unavailableReason = if (available) null else "播放器音频增强控制权已被其他音效占用",
                    )
                )
                available
            },
            onFailure = {
                onAvailabilityChanged(
                    PlayerAudioEffectAvailability(
                        volumeGainAvailable = false,
                        dspPresetAvailable = false,
                        unavailableReason = "此设备的音频会话不支持播放器音量增强",
                    )
                )
                false
            },
        )
    }

    private fun applyCurrentParameters(): Boolean {
        val dp = dynamicsProcessing
        if (dp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching {
                applyToDynamicsProcessing(dp, currentPreset, currentAmplification)
            }.onFailure {
                onAvailabilityChanged(
                    PlayerAudioEffectAvailability(
                        volumeGainAvailable = false,
                        dspPresetAvailable = false,
                        unavailableReason = "音频增强已不可用，请重新准备播放",
                    )
                )
            }.isSuccess
        }
        val le = loudnessEnhancer
        if (le != null) {
            return runCatching {
                le.setTargetGain(playerAmplificationMillibels(currentAmplification))
            }.onFailure {
                onAvailabilityChanged(
                    PlayerAudioEffectAvailability(
                        volumeGainAvailable = false,
                        dspPresetAvailable = false,
                        unavailableReason = "音频增强已不可用，请重新准备播放",
                    )
                )
            }.isSuccess
        }
        return false
    }

    private fun applyToDynamicsProcessing(
        effect: DynamicsProcessing,
        preset: PlayerAudioEffectPreset,
        amplification: Float,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val inputGainDb = amplificationToGainDb(amplification)
        effect.setInputGainAllChannelsTo(inputGainDb)

        val eq = DynamicsProcessing.Eq(
            true,
            preset != PlayerAudioEffectPreset.OFF,
            PlayerAudioEffectPreset.EQ_CUTOFF_FREQUENCIES_HZ.size,
        )
        PlayerAudioEffectPreset.EQ_CUTOFF_FREQUENCIES_HZ.forEachIndexed { index, cutoff ->
            eq.setBand(
                index,
                DynamicsProcessing.EqBand(true, cutoff, preset.bandGainsDb[index]),
            )
        }
        effect.setPreEqAllChannelsTo(eq)

        val mbc = DynamicsProcessing.Mbc(
            true,
            preset.enableDynamicCompression,
            MBC_BAND_COUNT,
        )
        if (preset.enableDynamicCompression) {
            // 低频与中高频两段压限，避免人声被低频泵浦效应拖暗。
            mbc.setBand(
                0,
                DynamicsProcessing.MbcBand(
                    true,
                    300f,
                    12f,
                    120f,
                    preset.mbcRatio,
                    preset.mbcThresholdDb + 2f,
                    6f,
                    -65f,
                    1f,
                    0f,
                    preset.mbcPostGainDb * 0.6f,
                ),
            )
            mbc.setBand(
                1,
                DynamicsProcessing.MbcBand(
                    true,
                    20000f,
                    8f,
                    90f,
                    preset.mbcRatio,
                    preset.mbcThresholdDb,
                    6f,
                    -65f,
                    1f,
                    0f,
                    preset.mbcPostGainDb,
                ),
            )
        }
        effect.setMbcAllChannelsTo(mbc)

        val limiterEnabled = preset.enableDynamicCompression || inputGainDb > 0.1f || preset != PlayerAudioEffectPreset.OFF
        val limiter = DynamicsProcessing.Limiter(
            true,
            limiterEnabled,
            0,
            2f,
            40f,
            10f,
            preset.limiterThresholdDb,
            0f,
        )
        effect.setLimiterAllChannelsTo(limiter)
    }

    fun release() {
        boundSessionId = 0
        dynamicsProcessing?.let { effect ->
            runCatching { effect.setControlStatusListener(null) }
            runCatching { effect.release() }
        }
        dynamicsProcessing = null
        loudnessEnhancer?.let { effect ->
            runCatching { effect.setControlStatusListener(null) }
            runCatching { effect.release() }
        }
        loudnessEnhancer = null
    }

    companion object {
        private const val CHANNEL_COUNT = 2
        private const val MBC_BAND_COUNT = 2
    }
}
