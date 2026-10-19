package com.android.purebilibili.feature.audio.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> ListenableFuture<T>.awaitBackgroundAudio(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        try {
            val value = get()
            if (continuation.isActive) continuation.resume(value)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }, { runnable -> runnable.run() })
    continuation.invokeOnCancellation { cancel(false) }
}

/** The caller owns handoff coroutines; this object only owns a controller connection. */
internal class BackgroundAudioConnection(
    private val context: Context,
    private val onState: (Player) -> Unit
) : AutoCloseable {
    var controller: MediaController? = null
        private set
    var active: Boolean = false
        private set
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (active) onState(player)
        }
    }

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        val future = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, BackgroundAudioService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(remote: MediaController) {
                    active = false
                    onState(remote)
                    remote.removeListener(listener)
                    remote.release()
                    if (controller === remote) controller = null
                }
            })
            .buildAsync()
        return try {
            future.awaitBackgroundAudio().also {
                controller = it
                it.addListener(listener)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            MediaController.releaseFuture(future)
            throw cancelled
        }
    }

    suspend fun transfer(snapshot: BackgroundAudioSnapshot, commitLocal: () -> Long?): Boolean = withTimeout(10_000L) {
        ContextCompat.startForegroundService(context,
            Intent(context, BackgroundAudioService::class.java).setAction(BackgroundAudioService.ACTION_PROMOTE))
        val remote = connect()
        val prepared = remote.sendCustomCommand(SessionCommand(AUDIO_PREPARE, Bundle.EMPTY), snapshot.toAudioBundle())
            .awaitBackgroundAudio()
        if (prepared.resultCode != SessionResult.RESULT_SUCCESS) return@withTimeout false
        active = true
        val positionMs = commitLocal()
        if (positionMs == null) {
            active = false
            return@withTimeout false
        }
        val committed = remote.sendCustomCommand(SessionCommand(AUDIO_COMMIT, Bundle.EMPTY),
            Bundle().apply { putLong("position_ms", positionMs) }).awaitBackgroundAudio()
        if (committed.resultCode != SessionResult.RESULT_SUCCESS) {
            active = false
            return@withTimeout false
        }
        onState(remote)
        true
    }

    suspend fun recover(): BackgroundAudioSnapshot? = withTimeout(5000L) {
        val remote = connect()
        val result = remote.sendCustomCommand(SessionCommand(AUDIO_SNAPSHOT, Bundle.EMPTY), Bundle.EMPTY).awaitBackgroundAudio()
        result.extras.audioSnapshot()?.also {
            active = true
            onState(remote)
        }
    }

    suspend fun reclaim(): BackgroundAudioSnapshot? = withTimeout(5000L) {
        val remote = controller ?: return@withTimeout null
        val result = remote.sendCustomCommand(SessionCommand(AUDIO_RETURN, Bundle.EMPTY), Bundle.EMPTY).awaitBackgroundAudio()
        check(result.resultCode == SessionResult.RESULT_SUCCESS) { "Remote audio has not acknowledged return" }
        active = false
        result.extras.audioSnapshot().also { detach() }
    }

    private fun detach() {
        active = false
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    override fun close() {
        controller?.sendCustomCommand(SessionCommand(AUDIO_CLOSE, Bundle.EMPTY), Bundle.EMPTY)
        detach()
        context.stopService(Intent(context, BackgroundAudioService::class.java))
    }
}
