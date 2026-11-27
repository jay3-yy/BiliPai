package com.android.purebilibili.feature.video.danmaku

import android.os.Build
import com.android.purebilibili.core.ui.adaptive.DeviceThermalTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DanmakuSurfacePolicyTest {

    @Test
    fun `enables independent SurfaceView on Android 11 plus during clean SurfaceView playback`() {
        assertTrue(
            shouldUseIndependentDanmakuSurface(
                useTextureVideoSurface = false,
                anime4kActive = false,
                capturePlayerPage = false,
                isInPipMode = false,
                sdkInt = Build.VERSION_CODES.R,
                preferCompatibleTextureSurface = false,
            )
        )
    }

    @Test
    fun `disables independent SurfaceView when incompatible with TextureView, Anime4K, backdrop capture, PiP, or older SDK`() {
        assertFalse(
            shouldUseIndependentDanmakuSurface(
                useTextureVideoSurface = true,
                anime4kActive = false,
                capturePlayerPage = false,
                isInPipMode = false,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                preferCompatibleTextureSurface = false,
            )
        )
        assertFalse(
            shouldUseIndependentDanmakuSurface(
                useTextureVideoSurface = false,
                anime4kActive = true,
                capturePlayerPage = false,
                isInPipMode = false,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                preferCompatibleTextureSurface = false,
            )
        )
        assertFalse(
            shouldUseIndependentDanmakuSurface(
                useTextureVideoSurface = false,
                anime4kActive = false,
                capturePlayerPage = true,
                isInPipMode = false,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                preferCompatibleTextureSurface = false,
            )
        )
        assertFalse(
            shouldUseIndependentDanmakuSurface(
                useTextureVideoSurface = false,
                anime4kActive = false,
                capturePlayerPage = false,
                isInPipMode = true,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                preferCompatibleTextureSurface = false,
            )
        )
        assertFalse(
            shouldUseIndependentDanmakuSurface(
                useTextureVideoSurface = false,
                anime4kActive = false,
                capturePlayerPage = false,
                isInPipMode = false,
                sdkInt = Build.VERSION_CODES.Q,
                preferCompatibleTextureSurface = false,
            )
        )
        assertFalse(
            shouldUseIndependentDanmakuSurface(
                useTextureVideoSurface = false,
                anime4kActive = false,
                capturePlayerPage = false,
                isInPipMode = false,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                preferCompatibleTextureSurface = true,
            )
        )
    }

    @Test
    fun `resolves LTPO target frame rate across playback states, video fps, and thermal tiers`() {
        // Paused / hidden releases frame rate lock for LTPO idle drop
        assertEquals(
            0f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 60f,
                playbackSpeed = 1f,
                isPlaying = false,
                thermalTier = DeviceThermalTier.NORMAL,
            ),
        )
        // Standard 30fps and 60fps video hint 60Hz (clean harmonic, drops 120Hz LTPO to 60Hz)
        assertEquals(
            60f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 30f,
                playbackSpeed = 1f,
                isPlaying = true,
                thermalTier = DeviceThermalTier.NORMAL,
            ),
        )
        assertEquals(
            60f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 60f,
                playbackSpeed = 1f,
                isPlaying = true,
                thermalTier = DeviceThermalTier.NORMAL,
            ),
        )
        // 24fps cinema and 120fps HFR keep native VSync under normal thermals (avoid 3:2 pulldown / HFR cap)
        assertEquals(
            0f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 24f,
                playbackSpeed = 1f,
                isPlaying = true,
                thermalTier = DeviceThermalTier.NORMAL,
            ),
        )
        assertEquals(
            0f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 120f,
                playbackSpeed = 1f,
                isPlaying = true,
                thermalTier = DeviceThermalTier.NORMAL,
            ),
        )
        // Elevated thermal caps 30fps video to 30Hz and 60fps video to 60Hz
        assertEquals(
            30f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 30f,
                playbackSpeed = 1f,
                isPlaying = true,
                thermalTier = DeviceThermalTier.ELEVATED,
            ),
        )
        assertEquals(
            60f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 60f,
                playbackSpeed = 1f,
                isPlaying = true,
                thermalTier = DeviceThermalTier.ELEVATED,
            ),
        )
        // Critical thermal forces 30Hz cap
        assertEquals(
            30f,
            resolveDanmakuTargetFrameRateHz(
                videoFrameRateHz = 60f,
                playbackSpeed = 1f,
                isPlaying = true,
                thermalTier = DeviceThermalTier.CRITICAL,
            ),
        )
    }
}
