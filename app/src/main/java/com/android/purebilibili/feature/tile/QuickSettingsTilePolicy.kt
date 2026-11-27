package com.android.purebilibili.feature.tile

import android.service.quicksettings.Tile
import androidx.media3.common.Player

internal data class QuickSettingsTilePresentation(
    val state: Int,
    val subtitle: String,
    val contentDescription: String,
)

/** 与 BackgroundAudioService 快照口径对齐：播完（STATE_ENDED）或空闲（STATE_IDLE）不再视为正在播放。 */
internal fun isAudioTileControllerActivelyPlaying(
    playWhenReady: Boolean,
    playbackState: Int,
    hasCurrentMediaItem: Boolean,
): Boolean = playWhenReady &&
    hasCurrentMediaItem &&
    playbackState != Player.STATE_ENDED &&
    playbackState != Player.STATE_IDLE

internal fun resolveAudioTilePresentation(
    isPlaying: Boolean,
    activeTitle: String?,
    savedTitle: String?,
    libraryProtected: Boolean,
    playingFallbackSubtitle: String,
    emptySubtitle: String,
    lockedSubtitle: String,
    tileLabel: String,
): QuickSettingsTilePresentation {
    val cleanActive = activeTitle?.trim()?.takeIf { it.isNotEmpty() }
    val cleanSaved = savedTitle?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        isPlaying -> {
            val subtitle = cleanActive ?: playingFallbackSubtitle
            QuickSettingsTilePresentation(
                state = Tile.STATE_ACTIVE,
                subtitle = subtitle,
                contentDescription = "$tileLabel · $subtitle",
            )
        }
        cleanActive != null -> QuickSettingsTilePresentation(
            state = Tile.STATE_INACTIVE,
            subtitle = cleanActive,
            contentDescription = "$tileLabel · $cleanActive",
        )
        libraryProtected -> QuickSettingsTilePresentation(
            state = Tile.STATE_INACTIVE,
            subtitle = lockedSubtitle,
            contentDescription = "$tileLabel · $lockedSubtitle",
        )
        cleanSaved != null -> QuickSettingsTilePresentation(
            state = Tile.STATE_INACTIVE,
            subtitle = cleanSaved,
            contentDescription = "$tileLabel · $cleanSaved",
        )
        else -> QuickSettingsTilePresentation(
            state = Tile.STATE_INACTIVE,
            subtitle = emptySubtitle,
            contentDescription = "$tileLabel · $emptySubtitle",
        )
    }
}

internal fun resolveIncognitoTilePresentation(
    enabled: Boolean,
    tileLabel: String,
    onSubtitle: String,
    offSubtitle: String,
): QuickSettingsTilePresentation {
    val subtitle = if (enabled) onSubtitle else offSubtitle
    return QuickSettingsTilePresentation(
        state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
        subtitle = subtitle,
        contentDescription = "$tileLabel · $subtitle",
    )
}
