package com.android.purebilibili.feature.home.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged

/** One observer on the card root; home and card consumers share the actual window visibility. */
internal class VideoCardVisibilityState {
    var visible by mutableStateOf(false)
        private set

    val observerModifier = Modifier.onVisibilityChanged(
        minFractionVisible = 0.01f,
        minDurationMs = 0L,
    ) { visible = it }
}

internal val LocalHomeCardVisibility = compositionLocalOf<VideoCardVisibilityState?> { null }

@Composable
internal fun rememberVideoCardVisibility(bvid: String, cid: Long): VideoCardVisibilityState =
    LocalHomeCardVisibility.current ?: remember(bvid, cid) { VideoCardVisibilityState() }
