package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode

@Stable
internal data class VideoDetailPlaybackActions(
    val resolveNextWatch: () -> com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion? = { null },
    val playNextWatch: (com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion) -> Boolean = { false },
    val changeQuality: (Int) -> Unit,
    val reloadVideo: () -> Unit,
    val switchCdn: () -> Unit,
    val switchCdnTo: (Int) -> Unit,
    val probeCdnCandidates: () -> Unit,
    val setAudioMode: (Boolean) -> Unit,
    val setSleepTimer: (Int?) -> Unit,
    val switchPage: (Int) -> Unit,
    val jumpToLastPlayedPart: () -> Unit = {},
    val dismissPartResumeNotice: () -> Unit = {},
    val openDownloadDialog: () -> Unit,
    val showDanmakuSendDialog: () -> Unit,
    val sendSameDanmaku: (String, (Result<Unit>) -> Unit) -> Unit,
    val skipSponsorSegment: () -> Unit,
    val dismissSponsorSkipButton: () -> Unit,
    val voteSponsorSegment: (Int) -> Unit,
    val markSponsorContributionBoundary: () -> Unit,
    val markWholeVideoAsSponsor: () -> Unit,
    val setSponsorContributionCategory: (String) -> Unit,
    val setSponsorContributionActionType: (String) -> Unit,
    val submitSponsorContribution: () -> Unit,
    val cancelSponsorContribution: () -> Unit,
    val notifyExplicitSeek: (Long) -> Unit,
    val setVideoCodec: (String) -> Unit,
    val setVideoSecondCodec: (String) -> Unit,
    val setAudioQuality: (Int) -> Unit,
    val applyPlaybackSpeed: (Float) -> Boolean,
    val changeAudioLanguage: (String?) -> Unit,
    val saveCover: () -> Unit,
    val downloadAudio: () -> Unit,
    val selectSubtitleTrack: (String) -> Unit,
    val showFavoriteFolderDialog: () -> Unit,
    val toggleFavoriteFolderSelection: (FavFolder) -> Unit,
    val saveFavoriteFolderSelection: () -> Unit,
    val dismissFavoriteFolderDialog: () -> Unit,
    val createFavoriteFolder: (String, String, Boolean) -> Unit,
    val retryAiSummary: () -> Unit,
    val createVideoNoteDraftFromAiSummary: () -> Unit,
    val openVideoNoteWithPostcard: (quote: String, moodNote: String, positionMs: Long) -> Unit = { _, _, _ -> },
    val openVideoNoteEditor: () -> Unit,
    val closeVideoNoteEditor: () -> Unit,
    val updateVideoNoteEditorDocument: (VideoNoteEditorDocument) -> Unit,
    val currentVideoNoteTimestamp: () -> com.android.purebilibili.feature.video.note.VideoNoteBlock.Timestamp?,
    val seekTo: (Long) -> Unit,
    val saveVideoNote: (VideoNoteEditorDocument?) -> Unit,
    val deleteVideoNote: () -> Unit,
    val retryVideoNote: () -> Unit,
    val loadMorePublicVideoNotes: () -> Unit,
    val openRootCommentComposer: () -> Unit,
    val replyTo: (ReplyItem) -> Unit,
    val markVideoNotInterested: () -> Unit,
    val likeDanmaku: (Long) -> Unit = {},
    val likeDanmakuToggle: (dmid: Long, like: Boolean) -> Unit = { _, _ -> },
    val likedDanmakuIds: kotlinx.coroutines.flow.StateFlow<Set<Long>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptySet()),
    val reportDanmaku: (dmid: Long, reason: Int) -> Unit = { _, _ -> },
    val recallDanmaku: (Long) -> Unit = {},
    val menuActions: com.android.purebilibili.feature.video.ui.overlay.PiliPlayerMenuActions =
        com.android.purebilibili.feature.video.ui.overlay.PiliPlayerMenuActions(),
    val retryPlayback: () -> Unit = reloadVideo
)

@Immutable
internal data class VideoDetailEngagementActions(
    val toggleFollow: () -> Unit,
    val followOwner: () -> Unit,
    val toggleFavorite: () -> Unit,
    val onFavoriteAction: (isLongPress: Boolean) -> Unit,
    val toggleLike: () -> Unit,
    val toggleDislike: () -> Unit = {},
    val openCoinDialog: () -> Unit,
    val doTripleAction: () -> Unit,
    val toggleWatchLater: () -> Unit
)

@Immutable
internal data class VideoDetailCommentActions(
    val loadComments: () -> Unit,
    val refreshComments: () -> Unit,
    val setSortMode: (CommentSortMode) -> Unit,
    val deleteComment: (Long) -> Unit,
    val startDissolve: (Long) -> Unit,
    val loadMoreSubReplies: () -> Unit,
    val refreshSubReplies: () -> Unit,
    val setSubReplySortMode: (SubReplySortMode) -> Unit,
    val openSubReply: (ReplyItem, Long) -> Unit,
    val openSubReplyConversation: (ReplyItem) -> Unit,
    val closeSubReplyConversation: () -> Unit,
    val closeSubReply: () -> Unit,
    val startSubDissolve: (Long) -> Unit,
    val deleteSubComment: (Long) -> Unit,
    val likeComment: (Long) -> Unit,
    val hateComment: (Long) -> Unit,
    val reportComment: (Long, Int) -> Unit,
    val toggleTopComment: (ReplyItem) -> Unit,
    val checkCommentFraud: (ReplyItem) -> Unit
)

@Immutable
internal data class VideoDetailNavigationActions(
    val back: () -> Unit,
    val home: () -> Unit,
    val toggleFullscreen: () -> Unit,
    val enterPortraitFullscreen: () -> Unit,
    val enterPip: () -> Unit,
    val navigateToAudioMode: () -> Unit
)
