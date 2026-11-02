package com.android.purebilibili.feature.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.data.model.response.PlayerInfoData
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.ui.pager.rememberPortraitPageHeatmap
import com.android.purebilibili.feature.video.ui.pager.rememberPortraitPagePlayerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortraitPageSupplementUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun metadataLoadsOnlyWhenActive_cancelsOffscreen_andReusesKnownEmptyCatalog() {
        var active by mutableStateOf(false)
        var cid by mutableStateOf(1L)
        var calls = 0
        var cancelled = false
        var loaded: PlayerInfoData? = null
        val load: suspend (String, Long) -> PlayerInfoData? = { _, part ->
            calls++
            if (calls == 1) {
                try { awaitCancellation() } finally { cancelled = true }
            } else PlayerInfoData(cid = part)
        }
        rule.setContent {
            val info by rememberPortraitPagePlayerInfo("BV_test", cid, active, false, load)
            androidx.compose.runtime.SideEffect { loaded = info }
        }
        rule.runOnIdle { assertEquals(0, calls); active = true }
        rule.runOnIdle { assertEquals(1, calls); active = false }
        rule.runOnIdle { assertTrue(cancelled); assertNull(loaded); active = true }
        rule.runOnIdle {
            assertEquals(2, calls)
            assertEquals(1L, loaded?.cid)
            assertTrue(loaded?.subtitle?.subtitles.isNullOrEmpty())
            active = false
        }
        rule.runOnIdle { active = true }
        rule.runOnIdle { assertEquals(2, calls); cid = 2L }
        rule.runOnIdle { assertEquals(3, calls); assertEquals(2L, loaded?.cid) }
    }

    @Test
    fun heatmapWaitsForFeatureAndVisibility_andReusesOfficialAndDensityResults() {
        var enabled by mutableStateOf(false)
        var active by mutableStateOf(false)
        var duration by mutableStateOf(0L)
        var cid by mutableStateOf(1L)
        var officialCalls = 0
        var densityCalls = 0
        val empty = PbpProgressData(1, emptyList())
        val density = PbpProgressData(2, listOf(1f, 2f))
        var loaded: PbpProgressData? = null
        val loadOfficial: suspend (String, Long, Long) -> PbpProgressData? = { _, _, _ ->
            officialCalls++; empty
        }
        val loadDensity: suspend (Long, Long) -> PbpProgressData? = { _, seconds ->
            assertEquals(60L, seconds)
            densityCalls++; density
        }
        rule.setContent {
            val heatmap by rememberPortraitPageHeatmap(
                "BV_test", cid, 100L, duration, enabled, active, false,
                loadOfficial, loadDensity,
            )
            androidx.compose.runtime.SideEffect { loaded = heatmap }
        }
        rule.runOnIdle { active = true }
        rule.runOnIdle { assertEquals(0, officialCalls); enabled = true }
        rule.runOnIdle {
            assertEquals(1, officialCalls)
            assertEquals(0, densityCalls)
            duration = 60_000L
        }
        rule.runOnIdle { assertEquals(density, loaded); active = false }
        rule.runOnIdle { enabled = false }
        rule.runOnIdle { active = true; enabled = true }
        rule.runOnIdle {
            assertEquals(1, officialCalls)
            assertEquals(1, densityCalls)
            cid = 2L
        }
        rule.runOnIdle { assertEquals(2, officialCalls); assertEquals(2, densityCalls) }
    }

    @Test
    fun leavingPageCancelsHeatmapRequest_beforeDensityDownloadBegins() {
        var active by mutableStateOf(true)
        var cancelled = false
        var densityCalls = 0
        val started = CompletableDeferred<Unit>()
        val loadOfficial: suspend (String, Long, Long) -> PbpProgressData? = { _, _, _ ->
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        }
        val loadDensity: suspend (Long, Long) -> PbpProgressData? = { _, _ -> densityCalls++; null }
        rule.setContent {
            rememberPortraitPageHeatmap(
                "BV_test", 1L, 100L, 60_000L, true, active, false,
                loadOfficial, loadDensity,
            )
        }
        rule.runOnIdle { assertTrue(started.isCompleted); active = false }
        rule.runOnIdle { assertTrue(cancelled); assertEquals(0, densityCalls) }
    }
}
