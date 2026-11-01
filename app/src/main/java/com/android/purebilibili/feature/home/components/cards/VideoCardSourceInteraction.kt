package com.android.purebilibili.feature.home.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.yield

/** Transient input state; the clicked navigation source is retained separately by identity. */
internal class VideoCardSourceInteraction {
    private var pressed by mutableStateOf(false)
    var clickPending by mutableStateOf(false)
        private set
    val preparing: Boolean get() = pressed || clickPending

    fun press() { pressed = true }
    fun release() { pressed = false }
    fun requestClick() { clickPending = true }
    fun cancel() {
        pressed = false
        clickPending = false
    }
}

/** Navigation is sequenced after both native source layers have actually been recorded. */
@Composable
internal fun VideoCardPreparedClickEffect(
    interaction: VideoCardSourceInteraction,
    capture: VideoCardCaptureState?,
    allowed: Boolean,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentAllowed by rememberUpdatedState(allowed)
    LaunchedEffect(interaction, allowed) {
        if (!allowed) interaction.cancel()
    }
    LaunchedEffect(interaction, interaction.clickPending, capture, allowed) {
        if (!allowed || !interaction.clickPending || capture == null) return@LaunchedEffect
        capture.awaitSourceRecorded()
        // Completion can be signalled inside draw; return to the UI queue before navigation.
        yield()
        // No suspension between this check, freezing the source and starting navigation.
        if (currentAllowed && interaction.clickPending) {
            currentOnClick()
            interaction.cancel()
        }
    }
}
