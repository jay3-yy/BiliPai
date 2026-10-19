package com.android.purebilibili.core.util

import androidx.media3.common.PlaybackException
import com.android.purebilibili.data.model.VideoLoadError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTelemetryPolicyTest {
    @Test
    fun playerCodesHaveStableCategories() {
        assertEquals("network", playbackErrorCategory(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals("timeout", playbackErrorCategory(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals("timeout", playbackErrorCategory(PlaybackException.ERROR_CODE_TIMEOUT))
        assertEquals("http", playbackErrorCategory(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals("decoder", playbackErrorCategory(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals("audio_output", playbackErrorCategory(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertEquals("other", playbackErrorCategory(-1))
    }

    @Test
    fun loadCategoriesDoNotContainApiMessagesOrMediaIdentity() {
        assertEquals("api", loadErrorCategory(VideoLoadError.ApiError(-500, "private details")))
        assertEquals("cooldown", loadErrorCategory(VideoLoadError.RateLimited(1000, "private video")))
        assertEquals("timeout", loadErrorCategory(VideoLoadError.Timeout))
        assertEquals("empty_play_url", loadErrorCategory(VideoLoadError.PlayUrlEmpty))
        assertEquals("vip_required", loadErrorCategory(VideoLoadError.VipRequired))
    }

    @Test
    fun oldReadyStateCannotSucceedBeforeSourceApplication() {
        val attempt = QualitySwitchTelemetryAttempt(64, 80, "user", 1000)
        assertNull(attempt.poll(1500, isCurrent = true, ready = true))
        attempt.applied(80, wasFallback = false)
        assertNull(attempt.poll(1700, isCurrent = true, ready = false))
        val result = attempt.poll(2000, isCurrent = true, ready = true)
        assertEquals("success", result?.outcome)
        assertEquals(1000L, result?.durationMs)
        assertNull(attempt.poll(2100, isCurrent = true, ready = true))
    }

    @Test
    fun differentActualQualityIsFallbackEvenWithoutServerFlag() {
        val attempt = QualitySwitchTelemetryAttempt(64, 120, "user", 0)
        attempt.applied(80, wasFallback = false)
        assertEquals("fallback", attempt.poll(1000, true, true)?.outcome)
    }

    @Test
    fun flaggedFallbackIsNotCountedAsTargetSuccess() {
        val attempt = QualitySwitchTelemetryAttempt(64, 80, "user", 0)
        attempt.applied(80, wasFallback = true)
        assertEquals("fallback", attempt.poll(1000, true, true)?.outcome)
    }

    @Test
    fun mediaChangeCancelsEvenWhenPlayerIsReady() {
        val attempt = QualitySwitchTelemetryAttempt(64, 80, "user", 0)
        attempt.applied(80, wasFallback = false)
        assertEquals("canceled", attempt.poll(1000, false, true)?.outcome)
        assertNull(attempt.finish("failed", "network", 2000))
    }

    @Test
    fun unreadyAttemptTimesOutAtBoundaryAndCannotLaterSucceed() {
        val attempt = QualitySwitchTelemetryAttempt(64, 80, "user", 0)
        assertNull(attempt.poll(29_999, true, false))
        assertEquals("timeout", attempt.poll(30_000, true, false)?.outcome)
        attempt.applied(80, wasFallback = false)
        assertNull(attempt.poll(30_001, true, true))
    }

    @Test
    fun blockedAndCanceledRequestsHaveOneResultWithNonnegativeDuration() {
        val attempt = QualitySwitchTelemetryAttempt(64, 120, "permission_fallback", 1000)
        val result = attempt.finish("blocked", "vip_required", 500)
        assertEquals("blocked", result?.outcome)
        assertEquals("permission_fallback", result?.source)
        assertEquals(0L, result?.durationMs)
        assertNull(result?.actualQuality)
        assertNull(attempt.finish("canceled", "superseded", 1500))
    }
}
