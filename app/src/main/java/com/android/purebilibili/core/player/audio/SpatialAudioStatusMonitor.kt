package com.android.purebilibili.core.player.audio

import android.content.Context
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * 监听 Android 13+ 系统 Spatializer（空间音频与头部追踪）实时状态。
 * API < 32 零开销直接返回不支持状态。
 */
internal object SpatialAudioStatusMonitor {

    fun snapshot(context: Context): SpatialAudioHardwareStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) {
            return SpatialAudioHardwareStatus()
        }
        val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
            ?: return SpatialAudioHardwareStatus()
        val spatializer = audioManager.spatializer
        val supported = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
        val headTracker = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            spatializer.isHeadTrackerAvailable
        } else {
            false
        }
        return SpatialAudioHardwareStatus(
            supported = supported,
            enabled = spatializer.isEnabled,
            available = spatializer.isAvailable,
            headTrackerAvailable = headTracker,
        )
    }

    fun observe(context: Context): Flow<SpatialAudioHardwareStatus> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) {
            return flowOf(SpatialAudioHardwareStatus())
        }
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(AudioManager::class.java)
            ?: return flowOf(SpatialAudioHardwareStatus())
        val spatializer = audioManager.spatializer
        return callbackFlow {
            fun emitCurrent() {
                trySend(snapshot(appContext))
            }
            val executor = appContext.mainExecutor
            val stateListener = object : Spatializer.OnSpatializerStateChangedListener {
                override fun onSpatializerEnabledChanged(spat: Spatializer, enabled: Boolean) = emitCurrent()
                override fun onSpatializerAvailableChanged(spat: Spatializer, available: Boolean) = emitCurrent()
            }
            val headTrackerListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spatializer.OnHeadTrackerAvailableListener { _, _ -> emitCurrent() }
            } else {
                null
            }
            spatializer.addOnSpatializerStateChangedListener(executor, stateListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && headTrackerListener != null) {
                spatializer.addOnHeadTrackerAvailableListener(executor, headTrackerListener)
            }
            emitCurrent()
            awaitClose {
                spatializer.removeOnSpatializerStateChangedListener(stateListener)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && headTrackerListener != null) {
                    spatializer.removeOnHeadTrackerAvailableListener(headTrackerListener)
                }
            }
        }.distinctUntilChanged()
    }
}
