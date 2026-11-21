package com.android.purebilibili.feature.video.player

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.compose.runtime.MutableState
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.widget.PlaybackWidgetProvider
import io.mockk.anyConstructed
import io.mockk.capture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MiniPlayerManagerLeakCleanupTest {

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun clearingExternalPlayerReleasesItsSessionWrapper() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        val manager = newMiniPlayerManager(mockContext())
        val player = mockk<ExoPlayer>(relaxed = true)
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns player
        externalPlayerState(manager).value = player
        manager.mediaSession = session
        try {
            assertTrue(manager.clearExternalPlayerIfMatches(player))

            verify(exactly = 1) { session.release() }
            assertNull(manager.mediaSession)
        } finally {
            manager.release()
        }
    }

    @Test
    fun clearingOldPlayerDoesNotReleaseSessionAlreadyBoundToAnotherPlayer() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        val manager = newMiniPlayerManager(mockContext())
        val oldPlayer = mockk<ExoPlayer>(relaxed = true)
        val newPlayer = mockk<ExoPlayer>(relaxed = true)
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns newPlayer
        externalPlayerState(manager).value = oldPlayer
        manager.mediaSession = session
        try {
            assertTrue(manager.clearExternalPlayerIfMatches(oldPlayer))

            verify(exactly = 0) { session.release() }
            assertSame(session, manager.mediaSession)
        } finally {
            manager.release()
        }
    }

    @Test
    fun navigationStopReleasesPlayerAfterItsLastDetailPageWasDisposed() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkObject(SettingsManager)
        every { SettingsManager.getMiniPlayerModeSync(any()) } returns SettingsManager.MiniPlayerMode.OFF
        every { SettingsManager.getStopPlaybackOnExitSync(any()) } returns true
        mockkObject(PlaybackWidgetProvider.Companion)
        every { PlaybackWidgetProvider.requestUpdate(any()) } returns Unit
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkConstructor(Handler::class)
        val mainQueue = mutableListOf<Runnable>()
        every { anyConstructed<Handler>().post(capture(mainQueue)) } returns true

        val manager = newMiniPlayerManager(mockContext())
        val player = mockk<ExoPlayer>(relaxed = true)
        externalPlayerState(manager).value = player
        try {
            manager.retainPlayerForPage(player)
            manager.releasePlayerFromPage(player)
            verify(exactly = 0) { player.release() }

            manager.markLeavingByNavigation(forceStop = true)
            mainQueue.toList().forEach(Runnable::run)

            assertNull(manager.player)
            verify(exactly = 1) { player.release() }
        } finally {
            manager.release()
        }
    }

    @Test
    fun pausedListeningBarDropsBuffersAfterTheLastDetailPageAndCanResume() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkObject(BackgroundManager, PlaybackWidgetProvider.Companion)
        every { BackgroundManager.isInBackground } returns false
        every { PlaybackWidgetProvider.requestUpdate(any()) } returns Unit
        val manager = newMiniPlayerManager(mockContext())
        val player = mockk<ExoPlayer>(relaxed = true)
        var playbackState = Player.STATE_READY
        every { player.playbackState } answers { playbackState }
        every { player.stop() } answers { playbackState = Player.STATE_IDLE }
        every { player.mediaItemCount } returns 1
        externalPlayerState(manager).value = player
        setCachedUiState(manager, mockk<VideoPlaybackUiState.Success>(relaxed = true))
        @Suppress("UNCHECKED_CAST")
        val activeState = MiniPlayerManager::class.java.getDeclaredField("isActive\$delegate")
            .apply { isAccessible = true }.get(manager) as MutableState<Boolean>
        activeState.value = true
        AudioNowPlayingSession.markListening()

        try {
            manager.retainPlayerForPage(player)
            manager.retainPlayerForPage(player)
            manager.releasePlayerFromPage(player)
            verify(exactly = 0) { player.stop() }

            manager.releasePlayerFromPage(player)
            verify(exactly = 1) { player.stop() }
            verify(exactly = 1) { player.clearVideoSurface() }
            verify(exactly = 0) { player.release() }
            assertSame(player, manager.player)
            assertNull(manager.cachedUiState)

            assertTrue(manager.togglePlayPause())
            verify(exactly = 1) { player.prepare() }
            verify(exactly = 1) { player.play() }
        } finally {
            AudioNowPlayingSession.dismiss()
            externalPlayerState(manager).value = null
            manager.release()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun externalPlayerState(manager: MiniPlayerManager): MutableState<ExoPlayer?> =
        MiniPlayerManager::class.java.getDeclaredField("_externalPlayer\$delegate")
            .apply { isAccessible = true }.get(manager) as MutableState<ExoPlayer?>

    private fun setCachedUiState(manager: MiniPlayerManager, state: VideoPlaybackUiState.Success) {
        MiniPlayerManager::class.java.getDeclaredField("cachedUiState")
            .apply { isAccessible = true }.set(manager, state)
    }

    @Test
    fun `dismiss releases media session when playback is closed`() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        val context = mockContext()
        val manager = newMiniPlayerManager(context)
        val mediaSession = mockk<MediaSession>(relaxed = true)
        manager.mediaSession = mediaSession

        try {
            manager.dismiss()

            verify(exactly = 1) { mediaSession.release() }
        } finally {
            BackgroundManager.removeListener(manager)
        }
    }

    @Test
    fun `release unregisters receiver and background listener`() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        val context = mockContext()
        val manager = newMiniPlayerManager(context)

        manager.release()

        verify(exactly = 1) { context.unregisterReceiver(any()) }
        assertFalse(backgroundListeners().contains(manager))
    }

    @Test
    fun `task removal stops local audio and leaves remote snapshot cleanup to its service`() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        val context = mockContext()
        val manager = newMiniPlayerManager(context)
        val player = mockk<ExoPlayer>(relaxed = true)
        val mediaSession = mockk<MediaSession>(relaxed = true)
        @Suppress("UNCHECKED_CAST")
        val playerState = MiniPlayerManager::class.java.getDeclaredField("_player\$delegate")
            .apply { isAccessible = true }.get(manager) as MutableState<ExoPlayer?>
        playerState.value = player
        manager.mediaSession = mediaSession
        AudioNowPlayingSession.markListening()
        AudioNowPlayingSession.publishBarOverlayVisible(true)

        try {
            manager.dismissForTaskRemoval()

            verify(exactly = 1) { player.pause() }
            verify(exactly = 1) { player.stop() }
            verify(exactly = 1) { mediaSession.release() }
            // 只停止本地播放服务，独立音频服务自行完成快照清理。
            verify(exactly = 1) { context.stopService(any()) }
            verify(exactly = 0) { context.startService(any()) }
            assertFalse(AudioNowPlayingSession.active.value)
            assertFalse(AudioNowPlayingSession.barOverlayVisible.value)
        } finally {
            playerState.value = null
            manager.release()
            AudioNowPlayingSession.dismiss()
            AudioNowPlayingSession.publishBarOverlayVisible(false)
        }
    }

    private fun newMiniPlayerManager(context: Context): MiniPlayerManager {
        val constructor = MiniPlayerManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(context)
    }

    private fun mockContext(): Context {
        val context = mockk<Context>(relaxed = true)
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.startService(any()) } returns ComponentName("com.android.purebilibili", "PlaybackService")
        return context
    }

    @Suppress("UNCHECKED_CAST")
    private fun backgroundListeners(): MutableList<BackgroundManager.BackgroundStateListener> {
        val field = BackgroundManager::class.java.getDeclaredField("listeners")
        field.isAccessible = true
        return field.get(BackgroundManager) as MutableList<BackgroundManager.BackgroundStateListener>
    }
}
