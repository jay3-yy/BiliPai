package com.android.purebilibili.feature.video.playback.coordinator

import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.playback.session.PlaybackSessionStore
import com.android.purebilibili.feature.video.policy.ResumePlaybackSuggestion
import com.android.purebilibili.feature.video.policy.resolveResumePlaybackPromptKey
import com.android.purebilibili.feature.video.policy.resolveResumePlaybackSuggestion
import com.android.purebilibili.feature.video.policy.shouldShowResumePlaybackPrompt
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import com.android.purebilibili.feature.video.viewmodel.resolvePlaybackEndAction

internal class PlaybackCoordinator(
    private val sessionStore: PlaybackSessionStore
) {

    fun resolvePlaybackEnded(
        behavior: PlaybackCompletionBehavior,
        autoPlayEnabled: Boolean,
        isExternalPlaylist: Boolean,
        externalPlaylistAutoContinueEnabled: Boolean,
        hasNextPage: Boolean,
        hasNextSeasonEpisode: Boolean
    ): PlaybackEndAction {
        val action = resolvePlaybackEndAction(
            behavior = behavior,
            autoPlayEnabled = autoPlayEnabled,
            isExternalPlaylist = isExternalPlaylist,
            externalPlaylistAutoContinueEnabled = externalPlaylistAutoContinueEnabled,
            hasNextPage = hasNextPage,
            hasNextSeasonEpisode = hasNextSeasonEpisode
        )
        sessionStore.recordCompletionAction(action)
        return action
    }

    /** 执行播完策略动作，返回是否触发了继续播放或重播。 */
    fun executePlaybackEndAction(
        action: PlaybackEndAction,
        repeatCurrent: () -> Unit,
        playNextInOrder: (ignoreSavedProgress: Boolean, autoPlay: Boolean) -> Boolean,
        playNextFromPlaylistLoop: (ignoreSavedProgress: Boolean, autoPlay: Boolean) -> Boolean,
        autoContinue: (ignoreSavedProgress: Boolean, autoPlay: Boolean) -> Unit
    ): Boolean {
        return when (action) {
            PlaybackEndAction.STOP -> false
            PlaybackEndAction.REPEAT_CURRENT -> {
                repeatCurrent()
                true
            }
            PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST -> playNextInOrder(true, true)
            PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP -> playNextFromPlaylistLoop(true, true)
            PlaybackEndAction.AUTO_CONTINUE -> {
                autoContinue(true, true)
                true
            }
        }
    }

    fun refreshResumeSuggestion(
        requestCid: Long,
        loadedInfo: ViewInfo,
        promptEnabled: Boolean,
        hasPromptedBefore: (String) -> Boolean,
        markPromptShown: (String) -> Unit,
        progressLookup: (String, Long) -> Long
    ): ResumePlaybackSuggestion? {
        val suggestion = resolveResumePlaybackSuggestion(
            requestCid = requestCid,
            loadedInfo = loadedInfo,
            progressLookup = progressLookup
        )
        val shouldShowPrompt = shouldShowResumePlaybackPrompt(
            suggestion = suggestion,
            promptEnabled = promptEnabled,
            hasPromptedBefore = hasPromptedBefore
        )
        val sessionSuggestion = if (shouldShowPrompt) suggestion else null
        if (shouldShowPrompt && suggestion != null) {
            markPromptShown(resolveResumePlaybackPromptKey(suggestion))
        }
        sessionStore.setResumeSuggestion(sessionSuggestion)
        return sessionSuggestion
    }

    fun dismissResumeSuggestion() {
        sessionStore.clearResumeSuggestion()
    }

    fun consumeResumeSuggestion(): ResumePlaybackSuggestion? {
        return sessionStore.consumeResumeSuggestion()
    }
}
