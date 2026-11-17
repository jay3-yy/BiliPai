package com.android.purebilibili.navigation3.predictiveback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.runtime.boundedNavGestureProgress

class MiuixPredictiveBackProgressTransitionTest {

    @Test
    fun `commit keeps held position and visual speed at limited preview release`() {
        for (release in listOf(0.2f, 0.6f, 0.9f)) {
            for (limit in listOf(0f, 0.5f, 1f)) {
                val step = (1f - release) * 0.0001f
                val held = resolveMiuixPredictiveBackVisualProgress(release, release, null, limit)
                val before = resolveMiuixPredictiveBackVisualProgress(release - step, release - step, null, limit)
                val committed = resolveMiuixPredictiveBackVisualProgress(release, release, NavSettlePhase.Commit, limit)
                val after = resolveMiuixPredictiveBackVisualProgress(release + step, release, NavSettlePhase.Commit, limit)
                assertEquals(held, committed, 0.0001f)
                assertEquals((held - before) / step, (after - committed) / step, 0.015f,
                    "release=$release limit=$limit")
            }
        }
    }

    @Test
    fun `releasing at driver finger cap does not jump to the full exit`() {
        val release = boundedNavGestureProgress(1f)
        for (limit in listOf(0f, 0.5f, 1f)) {
            val held = resolveMiuixPredictiveBackVisualProgress(release, 1f, null, limit)
            val samples = (0..100).map { step ->
                resolveMiuixPredictiveBackVisualProgress(
                    release + (1f - release) * step / 100f, 1f, NavSettlePhase.Commit, limit,
                )
            }
            assertEquals(held, samples.first(), 0.0001f)
            assertEquals(1f, samples.last(), 0.0001f)
            assertTrue(samples.all { it in 0f..1f })
            assertTrue(samples.zipWithNext().all { (previous, next) -> next >= previous })
        }
    }

    @Test
    fun `preview maximum applies while held and commit completes continuously`() {
        assertEquals(
            0.3f,
            resolveMiuixPredictiveBackVisualProgress(
                rawProgress = 0.6f,
                gestureProgress = 0.6f,
                settlePhase = null,
                maxPreviewFraction = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            0.3f,
            resolveMiuixPredictiveBackVisualProgress(
                rawProgress = 0.6f,
                gestureProgress = 0.6f,
                settlePhase = NavSettlePhase.Commit,
                maxPreviewFraction = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            0.65f,
            resolveMiuixPredictiveBackVisualProgress(
                rawProgress = 0.8f,
                gestureProgress = 0.6f,
                settlePhase = NavSettlePhase.Commit,
                maxPreviewFraction = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveMiuixPredictiveBackVisualProgress(
                rawProgress = 1f,
                gestureProgress = 0.6f,
                settlePhase = NavSettlePhase.Commit,
                maxPreviewFraction = 0.5f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `default maximum keeps gesture progress one to one`() {
        assertEquals(
            0.6f,
            resolveMiuixPredictiveBackVisualProgress(
                rawProgress = 0.6f,
                gestureProgress = 0.6f,
                settlePhase = null,
                maxPreviewFraction = 1f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `cancel follows limited preview and programmatic back stays complete`() {
        assertEquals(
            0.1f,
            resolveMiuixPredictiveBackVisualProgress(
                rawProgress = 0.2f,
                gestureProgress = 0.6f,
                settlePhase = NavSettlePhase.Cancel,
                maxPreviewFraction = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            0.8f,
            resolveMiuixPredictiveBackVisualProgress(
                rawProgress = 0.8f,
                gestureProgress = null,
                settlePhase = NavSettlePhase.Programmatic,
                maxPreviewFraction = 0.5f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `progress control is selected only for Miuix style`() {
        assertTrue(
            shouldUseMiuixPredictiveBackProgress(
                animation = BiliPaiPredictiveBackAnimationStyle.MIUIX,
                enabled = true,
            ),
        )
        assertFalse(
            shouldUseMiuixPredictiveBackProgress(
                animation = BiliPaiPredictiveBackAnimationStyle.MIUIX,
                enabled = false,
            ),
        )
        assertFalse(
            shouldUseMiuixPredictiveBackProgress(
                animation = BiliPaiPredictiveBackAnimationStyle.SCALE,
                enabled = true,
            ),
        )
    }

    @Test
    fun `gesture conflict routes keep their excluded or dedicated transitions`() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/navigation3/BiliPaiNavEntryProvider.kt"),
            File("src/main/java/com/android/purebilibili/navigation3/BiliPaiNavEntryProvider.kt"),
        ).first(File::exists).readText()

        listOf(
            "Search",
            "JsPluginContent",
            "ExternalMedia",
            "OfflineVideoPlayer",
            "AudioMode",
            "BangumiPlayer",
            "MusicDetail",
            "NativeMusic",
            "Web",
        ).forEach { keyName ->
            val entryBlock = source
                .substringAfter("entry<BiliPaiNavKey.$keyName>(")
                .substringBefore("content = content")
            assertTrue(
                entryBlock.contains("transition = predictiveBackExcludedTransition"),
                "$keyName must not use the limited predictive-back transition",
            )
        }
        val liveEntry = source.substringAfter("entry<BiliPaiNavKey.Live>(")
            .substringBefore("content = content")
        assertTrue(liveEntry.contains("?.let(liveContainerTransitions::get) ?: pageTransition"))
        assertTrue(liveEntry.contains("swipeDismiss = NavSwipeDirection.None"))
        assertTrue(
            source.substringAfter("entry<BiliPaiNavKey.VideoDetail>(")
                .substringBefore("content = content")
                .contains("transition = videoCardTransition"),
        )
        assertTrue(
            source.substringAfter("entry<BiliPaiNavKey.Story>(")
                .substringBefore("content = content")
                .contains("transition = fullscreenVideoCardTransition"),
        )
    }
}
