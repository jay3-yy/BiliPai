package com.android.purebilibili.feature.video.playback.resolver

import com.android.purebilibili.feature.video.player.ExternalPlaylistSource

internal enum class AudioNextPlaybackStrategy {
    PLAY_EXTERNAL_PLAYLIST,
    PAGE_THEN_SEASON_THEN_RELATED
}

internal enum class PlayInOrderNextSource {
    PAGE_OR_SEASON,
    PLAYLIST,
    NONE
}

internal enum class PlaybackNavigationTarget {
    PAGE_OR_SEASON,
    PLAYLIST,
    DIRECT_QUEUE
}

private fun resolvePlayInOrderSource(
    hasPageOrSeasonTarget: Boolean,
    hasPlaylistTarget: Boolean
): PlayInOrderNextSource {
    return when {
        hasPageOrSeasonTarget -> PlayInOrderNextSource.PAGE_OR_SEASON
        hasPlaylistTarget -> PlayInOrderNextSource.PLAYLIST
        else -> PlayInOrderNextSource.NONE
    }
}

internal fun resolvePlayInOrderNextSource(
    hasNextPage: Boolean,
    hasNextSeasonEpisode: Boolean,
    hasNextPlaylistItem: Boolean
): PlayInOrderNextSource {
    return resolvePlayInOrderSource(
        hasPageOrSeasonTarget = hasNextPage || hasNextSeasonEpisode,
        hasPlaylistTarget = hasNextPlaylistItem
    )
}

internal fun resolvePlayInOrderPreviousSource(
    hasPreviousPage: Boolean,
    hasPreviousSeasonEpisode: Boolean,
    hasPreviousPlaylistItem: Boolean
): PlayInOrderNextSource {
    return resolvePlayInOrderSource(
        hasPageOrSeasonTarget = hasPreviousPage || hasPreviousSeasonEpisode,
        hasPlaylistTarget = hasPreviousPlaylistItem
    )
}

internal fun resolveAudioNextPlaybackStrategy(
    isExternalPlaylist: Boolean,
    externalPlaylistSource: ExternalPlaylistSource
): AudioNextPlaybackStrategy {
    if (!isExternalPlaylist || externalPlaylistSource == ExternalPlaylistSource.NONE) {
        return AudioNextPlaybackStrategy.PAGE_THEN_SEASON_THEN_RELATED
    }
    return AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST
}

internal fun resolvePlaybackNavigationTargets(
    strategy: AudioNextPlaybackStrategy,
    hasPageOrSeasonTarget: Boolean,
    hasPlaylistTarget: Boolean
): List<PlaybackNavigationTarget> {
    if (strategy == AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST) {
        // 收藏夹/稍后再看等外部队列：只按列表顺序续播，不插入分P/合集内下一集。
        return buildList {
            if (hasPlaylistTarget) {
                add(PlaybackNavigationTarget.PLAYLIST)
            }
            add(PlaybackNavigationTarget.DIRECT_QUEUE)
        }
    }

    return buildList {
        if (hasPageOrSeasonTarget) {
            add(PlaybackNavigationTarget.PAGE_OR_SEASON)
        }
        if (hasPlaylistTarget) {
            add(PlaybackNavigationTarget.PLAYLIST)
        }
        add(PlaybackNavigationTarget.DIRECT_QUEUE)
    }
}
