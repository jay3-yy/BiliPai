package com.android.purebilibili.feature.video.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.core.plugin.PluginInfo
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.SponsorBlockSessionApi
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.feature.plugin.CdnTransferRuntime
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.plugin.sdk.PlayerPluginApi
import com.android.purebilibili.plugin.sdk.SkipAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlaybackViewModelPluginSessionTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun disablingSponsorRestoresVolumeAndClearsOldReviewAndButton() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.prepareMutedReview()
            fixture.enabled.value = emptyList()
            runCurrent()
            fixture.assertReleased()
        }
    }

    @Test fun changingCidEndsOldSessionAndClearsItsVolumeButtonAndReview() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.prepareMutedReview()
            fixture.state.value = success(cid = 2L)
            runCurrent()
            fixture.assertReleased(expectedPhase = SponsorContributionPhase.READY)
            assertEquals(2, fixture.definition.sessions.size)
            assertEquals(2L, fixture.definition.sessions.last().loadedCid)
        }
    }

    @Test fun leavingPlayerRestoresVolumeAndClearsOldReviewAndButton() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.prepareMutedReview()
            fixture.owner.callPrivate("onCleared")
            fixture.assertReleased()
            fixture.owner.callPrivate("onCleared")
            assertEquals(1, fixture.definition.sessions.first().ends)
        }
    }

    @Test fun suspendedPositionActionCannotMuteAfterPluginWasDisabled() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.loadPlayer()
            val action = CompletableDeferred<SkipAction>()
            fixture.definition.sessions.single().pendingAction = action
            fixture.owner.callPrivate("startPlaybackTasks")
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            fixture.enabled.value = emptyList()
            runCurrent()
            action.complete(SkipAction.Mute(60_000L, "late mute", showToast = false))
            runCurrent()
            fixture.assertReleased()
        }
    }

    @Test fun pausedPluginWaitsForEvents_andResumesPositionChecksWhenPlaying() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.playing = false
            fixture.loadPlayer()
            fixture.owner.callPrivate("startPlaybackTasks")
            runCurrent()
            val session = fixture.definition.sessions.single()
            val initialUpdates = session.updates
            val readsWhilePaused = fixture.positionReads
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals(initialUpdates, session.updates)
            assertEquals(readsWhilePaused, fixture.positionReads)

            fixture.position = 8_000L
            fixture.notifyPlayerChanged()
            runCurrent()
            assertEquals(initialUpdates + 1, session.updates)
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals(initialUpdates + 1, session.updates)

            fixture.playing = true
            fixture.notifyPlayerChanged()
            runCurrent()
            fixture.position = 9_000L
            advanceTimeBy(750L)
            runCurrent()
            assertEquals(initialUpdates + 2, session.updates)
        }
    }

    @Test fun disablingLastPlugin_stopsPeriodicPlayerReadsWhenOtherSamplingIsDisabled() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.loadPlayer()
            fixture.owner.callPrivate("startPlaybackTasks")
            runCurrent()
            fixture.enabled.value = emptyList()
            runCurrent()
            val readsAfterDisable = fixture.positionReads
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals(readsAfterDisable, fixture.positionReads)
        }
    }

    @Test fun failedPluginRetriesAtItsDeadline_evenWhilePausedWithoutPositionPolling() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.playing = false
            fixture.definition.failNextLoads = 1
            fixture.loadPlayer()
            assertEquals(1, fixture.definition.loads)
            advanceTimeBy(1_999L)
            runCurrent()
            assertEquals(1, fixture.definition.loads)
            advanceTimeBy(1L)
            runCurrent()
            assertEquals(2, fixture.definition.loads)
            assertTrue(fixture.owner.sponsorProgressMarkers.value.isNotEmpty())
        }
    }

    @Test fun disablingPlugin_cancelsItsPendingLoadRetry() = runTest(dispatcher) {
        withFixture { fixture ->
            fixture.definition.failNextLoads = 1
            fixture.loadPlayer()
            fixture.enabled.value = emptyList()
            runCurrent()
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals(1, fixture.definition.loads)
            assertEquals(1, fixture.definition.sessions.single().ends)
        }
    }

    private suspend fun TestScope.withFixture(block: suspend (Fixture) -> Unit) {
        mockkObject(SettingsManager, PluginManager, AnalyticsHelper, CdnTransferRuntime)
        every { AnalyticsHelper.isCollectionEnabled() } returns false
        every { AnalyticsHelper.collectionEnabled } returns MutableStateFlow(false)
        every { CdnTransferRuntime.enabled } returns false
        every { CdnTransferRuntime.enabledFlow } returns MutableStateFlow(false)
        val application = mockk<Application>(relaxed = true)
        every { application.applicationContext } returns application
        every { SettingsManager.getPlaybackCdnPreference(application) } returns flowOf("base_url")
        val definition = Definition()
        val enabled = MutableStateFlow(listOf(PluginInfo(definition, true)))
        every { PluginManager.pluginsFlow } returns enabled
        every { PluginManager.getEnabledPlayerPlugins() } answers {
            if (enabled.value.any { it.enabled }) listOf(definition) else emptyList()
        }
        every { PluginManager.isEnabled(any()) } answers { enabled.value.any { it.enabled } }
        val owner = VideoPlaybackViewModel(application)
        val fixture = Fixture(owner, definition, enabled, this)
        try {
            block(fixture)
        } finally {
            owner.viewModelScope.cancel()
            unmockkObject(SettingsManager, PluginManager, AnalyticsHelper, CdnTransferRuntime)
        }
    }

    private inner class Fixture(
        val owner: VideoPlaybackViewModel,
        val definition: Definition,
        val enabled: MutableStateFlow<List<PluginInfo>>,
        private val testScope: TestScope,
    ) {
        @Suppress("UNCHECKED_CAST")
        val state = owner.field("_uiState") as MutableStateFlow<VideoPlaybackUiState>
        var volume = 0.65f
        var position = 1_000L
        var playing = true
        var positionReads = 0
        private val player = mockk<ExoPlayer>(relaxed = true).also { player ->
            every { player.volume } answers { volume }
            every { player.volume = any() } answers { volume = firstArg() }
            every { player.currentPosition } answers { positionReads++; position }
            every { player.duration } returns 120_000L
            every { player.isPlaying } answers { playing }
        }

        fun notifyPlayerChanged() {
            val listener = owner.field("playbackEndListener") as Player.Listener
            listener.onEvents(player, mockk<Player.Events>())
        }

        suspend fun loadPlayer() {
            owner.setField("exoPlayer", player)
            owner.field("playbackUseCase")!!.setField("exoPlayer", player)
            state.value = success(cid = 1L)
            testScope.runCurrent()
        }

        suspend fun prepareMutedReview() {
            loadPlayer()
            owner.callPrivate("startPlaybackTasks")
            testScope.runCurrent()
            testScope.advanceTimeBy(1_000L)
            testScope.runCurrent()
            assertEquals(0f, volume)
            position = 3_000L
            testScope.advanceTimeBy(1_000L)
            testScope.runCurrent()
            assertTrue(owner.showSkipButton.value)
            assertTrue(owner.sponsorProgressMarkers.value.isNotEmpty())
            assertEquals("segment", owner.currentSponsorSegment.value?.UUID)
            owner.markSponsorContributionBoundary()
            position = 5_000L
            owner.markSponsorContributionBoundary()
            assertEquals(SponsorContributionPhase.REVIEW, owner.sponsorContributionUiState.value.phase)
        }

        fun assertReleased(expectedPhase: SponsorContributionPhase = SponsorContributionPhase.HIDDEN) {
            assertEquals(0.65f, volume)
            assertNull(owner.field("sponsorMutedOriginalVolume"))
            assertNull(owner.field("sponsorMuteRestoreAtMs"))
            assertFalse(owner.showSkipButton.value)
            assertNull(owner.currentSponsorSegment.value)
            assertTrue(owner.sponsorProgressMarkers.value.isEmpty())
            assertNull(owner.field("sponsorContributionRequest"))
            assertEquals(expectedPhase, owner.sponsorContributionUiState.value.phase)
            assertEquals(1, definition.sessions.first().ends)
        }
    }

    private class Definition : BuiltinPlugin, PlayerPluginApi {
        override val id = "test_sponsor_session"
        override val name = "Test sponsor"
        override val description = ""
        override val version = "1"
        val sessions = mutableListOf<Session>()
        var failNextLoads = 0
        var loads = 0
        override fun createSession() = Session {
            loads++
            if (failNextLoads > 0) {
                failNextLoads--
                throw IllegalStateException("load failed")
            }
        }.also(sessions::add)
    }

    private class Session(private val load: () -> Unit) : SponsorBlockSessionApi {
        var ends = 0
        var updates = 0
        var loadedCid = 0L
        var pendingAction: CompletableDeferred<SkipAction>? = null
        private val segment = SponsorSegment(
            segment = listOf(1f, 30f),
            UUID = "segment",
            category = SponsorCategory.SPONSOR,
        )
        override suspend fun onVideoLoad(bvid: String, cid: Long) { load(); loadedCid = cid }
        override suspend fun onPositionUpdate(positionMs: Long): SkipAction {
            pendingAction?.let { return it.await() }
            return if (updates++ == 0) SkipAction.Mute(60_000L, "mute", showToast = false)
            else SkipAction.ShowButton(30_000L, "skip", "segment")
        }
        override fun onVideoEnd() { ends++ }
        override fun markAsSkipped(segmentId: String): SponsorSegment? = null
        override fun getProgressMarkers(): List<SponsorProgressMarker> =
            if (loadedCid == 1L) listOf(
                SponsorProgressMarker("segment", SponsorCategory.SPONSOR, 1_000L, 30_000L),
            ) else emptyList()
        override fun getSegments(): List<SponsorSegment> =
            if (loadedCid == 1L) listOf(segment) else emptyList()
        override fun getActiveSegment(): SponsorSegment = segment
        override fun isCommunityContributionEnabled() = true
        override fun getCommunityServerBaseUrl() = "https://community.example"
        override suspend fun uploadViewedSegmentIfEnabled(segmentId: String) = Unit
        override suspend fun voteOnCommunitySegment(segmentId: String, voteType: Int) = Result.success(Unit)
        override suspend fun submitCommunitySegment(
            bvid: String, cid: Long, videoDurationSeconds: Float, startMs: Long, endMs: Long,
            category: String, actionType: String,
        ): Result<List<SponsorSegment>> = Result.success(emptyList())
    }

    private fun success(cid: Long) = VideoPlaybackUiState.Success(
        info = ViewInfo(bvid = "BV_session", cid = cid), playUrl = "",
    )

    private fun Any.field(name: String): Any? = javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(this)
    }

    private fun Any.setField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            it.set(this, value)
        }
    }

    private fun Any.callPrivate(name: String) {
        javaClass.getDeclaredMethod(name).let {
            it.isAccessible = true
            it.invoke(this)
        }
    }
}
