package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.store.ContentDisplayPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerControlsTimeoutPolicyTest {
    @Test
    fun `default preserves each player timing and never schedules no deadline`() {
        assertEquals(3_000L, resolvePlayerControlsTimeoutMillis(0, 3_000L))
        assertEquals(4_000L, resolvePlayerControlsTimeoutMillis(0, 4_000L))
        assertNull(resolvePlayerControlsTimeoutMillis(-1, 4_000L))
    }

    @Test
    fun `selected durations apply consistently and invalid imports use the default`() {
        listOf(8, 15, 30).forEach { selection ->
            assertEquals(selection * 1_000L, resolvePlayerControlsTimeoutMillis(selection, 3_000L))
            assertEquals(selection * 1_000L, resolvePlayerControlsTimeoutMillis(selection, 4_000L))
        }
        listOf(-2, 1, 60, Int.MAX_VALUE).forEach { invalid ->
            assertEquals(0, ContentDisplayPreferences.normalizePlayerControlsTimeout(invalid))
            assertEquals(4_000L, resolvePlayerControlsTimeoutMillis(invalid, 4_000L))
        }
    }
}
