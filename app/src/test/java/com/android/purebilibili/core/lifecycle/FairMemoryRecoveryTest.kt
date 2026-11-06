package com.android.purebilibili.core.lifecycle

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.AudioPlaybackCheckpoint
import com.android.purebilibili.core.store.MultiProcessPreferences
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class FairMemoryRecoveryTest {
    private fun snapshot() = FairMemoryRecoverySnapshot(
        sessionId = "previous-process",
        accountMid = 42L,
        navigation = FairMemoryNavigationState(
            listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.VideoDetail("BV1", cid = 10L)),
            bottomRoute = "history",
        ),
        savedAtMs = 1234L,
    )

    @Test
    fun snapshotRoundTripPreservesNavigationAndMatchingPlaybackPart() {
        val saved = snapshot()
        val updated = withFairMemoryPlaybackPosition(saved.navigation,
            AudioPlaybackCheckpoint(42L, "BV1", 20L, 7500L))
        val raw = FairMemoryRecoveryStore.json.encodeToString(saved.copy(navigation = updated))
        val restored = FairMemoryRecoveryStore.json.decodeFromString(FairMemoryRecoverySnapshot.serializer(), raw)
        val video = restored.navigation.backStack.last() as BiliPaiNavKey.VideoDetail
        assertEquals(20L, video.cid)
        assertEquals(7500L, video.resumePositionMs)
        assertEquals("history", restored.navigation.bottomRoute)
        assertEquals(BiliPaiNavKey.MainHost, restored.navigation.backStack.first())
        assertEquals(saved.navigation, withFairMemoryPlaybackPosition(saved.navigation,
            AudioPlaybackCheckpoint(42L, "BV2", 99L, 9000L)))
    }

    @Test
    fun accountSessionAndExplicitEntriesControlRecovery() {
        val saved = snapshot()
        assertTrue(canRestoreFairMemorySnapshot(saved, "new-process", 42L, false))
        assertFalse(canRestoreFairMemorySnapshot(saved, "previous-process", 42L, false))
        assertFalse(canRestoreFairMemorySnapshot(saved, "new-process", 43L, false))
        assertFalse(canRestoreFairMemorySnapshot(saved, "new-process", 42L, true))
        assertTrue(canRestoreFairMemorySnapshot(saved, "new-process", 42L, false, "BV1"))
        assertFalse(canRestoreFairMemorySnapshot(saved, "new-process", 42L, false, "BV2"))
        assertFalse(canRestoreFairMemorySnapshot(saved, "new-process", 42L, false,
            playbackAccountMid = 99L))
    }

    @Test
    fun episodeResumeRequiresTheSameEpisodeAndEphemeralMediaReturnsToSource() {
        val episode = FairMemoryNavigationState(
            listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.BangumiPlayer(1L, 2L)), "home")
        val playback = AudioPlaybackCheckpoint(42L, "BV1", 20L, 5000L)
        assertEquals(episode, withFairMemoryPlaybackPosition(episode, playback, 3L))
        assertEquals(5000L, (withFairMemoryPlaybackPosition(episode, playback, 2L)
            .backStack.last() as BiliPaiNavKey.BangumiPlayer).resumePositionMs)
        val source = BiliPaiNavKey.JsPluginContent("source")
        assertEquals(listOf(BiliPaiNavKey.MainHost, source), restorableFairMemoryNavigation(
            FairMemoryNavigationState(listOf(BiliPaiNavKey.MainHost, source,
                BiliPaiNavKey.ExternalMedia("expired")), "home")).backStack)
    }

    @Test
    fun persistedCheckpointIsConsumedOnlyOnce() = runTest {
        val context: Application = RuntimeEnvironment.getApplication()
        val saved = snapshot().copy(accountMid = TokenManager.midCache ?: 0L)
        val store = MultiProcessPreferences.get(context, "fair_memory_recovery")
        store.edit {
            it[stringPreferencesKey("navigation_snapshot")] = FairMemoryRecoveryStore.json.encodeToString(saved)
        }
        assertEquals(saved, FairMemoryRecoveryStore.consume(context, false, null))
        assertNull(FairMemoryRecoveryStore.consume(context, false, null))
    }

    @Test
    fun continuingInSameProcessInvalidatesThePendingRecovery() = runTest {
        val context: Application = RuntimeEnvironment.getApplication()
        FairMemoryRecoveryStore.save(context, snapshot().navigation, TokenManager.midCache ?: 0L)
        assertNull(FairMemoryRecoveryStore.consume(context, false, null))
        FairMemoryRecoveryStore.invalidateCurrentProcessCheckpoint(context)
        assertNull(MultiProcessPreferences.get(context, "fair_memory_recovery").data
            .first()[stringPreferencesKey("navigation_snapshot")])
    }
}
