package com.android.purebilibili.feature.audio.bgm

import com.android.purebilibili.core.ui.AppSurfaceTokens

import com.android.purebilibili.core.ui.AppContentColors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.video.ui.components.*
import com.android.purebilibili.feature.video.viewmodel.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun BgmDetailScreen(
    musicId: String,
    aid: Long,
    cid: Long,
    showVideos: Boolean,
    onBack: () -> Unit,
    onVideosClick: () -> Unit,
    onVideoClick: (String, Long, String) -> Unit,
    onUserClick: (Long) -> Unit,
    onCommentClick: (Long, Long, Long, Int) -> Unit,
    onLinkClick: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BgmDetailViewModel = composeViewModel(),
    commentViewModel: VideoCommentViewModel = composeViewModel(key = "bgm-comments-$musicId"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comments by commentViewModel.commentState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var composer by rememberSaveable(musicId) { mutableStateOf(false) }
    var submittedSessionId by remember(musicId) { mutableStateOf<Long?>(null) }
    var emotePackages by remember { mutableStateOf<List<EmotePackage>>(emptyList()) }
    LaunchedEffect(composer) {
        if (composer && emotePackages.isEmpty()) emotePackages = com.android.purebilibili.data.repository.CommentRepository.getEmotePackages().getOrDefault(emptyList())
    }
    LaunchedEffect(musicId, aid, cid, showVideos) { viewModel.initialize(musicId, aid, cid, showVideos) }
    val info = state.detail?.musicComment
    LaunchedEffect(info?.oid, info?.pageType, showVideos, state.loading, state.revision) {
        if (!state.loading && !showVideos && info != null && info.oid > 0 && state.detail?.flowAttr?.noComment != true) {
            commentViewModel.init(aid = info.oid, commentType = resolveBgmCommentType(info), expectedReplyCount = info.nums)
        }
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(comments.isSending, comments.sendError, comments.composerDrafts.completedSessionId) {
        if (submittedSessionId != null && !comments.isSending) {
            submittedSessionId = null
            comments.sendError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(comments.composerDrafts.sessionId, comments.composerDrafts.completedSessionId) {
        if (comments.composerDrafts.completedSessionId == comments.composerDrafts.sessionId) {
            composer = false
        }
    }
    fun compose(reply: ReplyItem? = null) {
        if (TokenManager.sessDataCache.isNullOrBlank()) { onLogin(); return }
        if (!comments.canInputComment) { Toast.makeText(context, "当前评论区暂不可发言", Toast.LENGTH_SHORT).show(); return }
        if (reply == null) commentViewModel.cancelReply() else commentViewModel.replyTo(reply)
        commentViewModel.beginCommentDraft()
        composer = true
    }
    BgmDetailContent(
        state = state, comments = comments, showVideos = showVideos,
        onBack = onBack, onRetry = {
            if (!state.updatingWish) { commentViewModel.clearForVideoChange(); viewModel.refresh() }
        }, onVideosClick = onVideosClick,
        onVideoClick = onVideoClick, onUserClick = onUserClick, onWish = viewModel::toggleWish,
        onShare = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${state.detail?.musicTitle.orEmpty()} https://music.bilibili.com/h5/music-detail?music_id=${android.net.Uri.encode(musicId)}")
            }, "分享音乐"))
        },
        onCompose = { compose() }, onReply = { compose(it) },
        onSort = commentViewModel::setSortMode, onLoadMore = commentViewModel::loadComments,
        onCommentClick = { root, target -> info?.let { onCommentClick(it.oid, root, target, resolveBgmCommentType(it)) } },
        onCommentLike = commentViewModel::likeComment, onCommentHate = commentViewModel::hateComment,
        onCommentDelete = commentViewModel::deleteComment, onCommentReport = commentViewModel::reportComment,
        onLinkClick = onLinkClick, modifier = modifier,
    )
    val composerSessionId = comments.composerDrafts.sessionId
    val composerReplyRpid = comments.replyTarget?.rpid
    val composerDraft = comments.composerDrafts.drafts[commentComposerDraftKey(composerReplyRpid)]
    val composerKey = remember(info?.oid, info?.pageType, composerReplyRpid, composerSessionId) {
        "bgm:${info?.oid}:${info?.let(::resolveBgmCommentType)}:$composerReplyRpid:$composerSessionId"
    }
    CommentInputDialog(
        visible = composer,
        draftKey = composerKey,
        onDismiss = {
            if (commentViewModel.commentState.value.composerDrafts.sessionId == composerSessionId) {
                composer = false
                commentViewModel.cancelReply()
            }
        },
        onSend = { text, images, sync ->
            val latest = commentViewModel.commentState.value
            if (composer && latest.composerDrafts.sessionId == composerSessionId &&
                latest.composerDrafts.completedSessionId != composerSessionId &&
                latest.replyTarget?.rpid == composerReplyRpid && !latest.isSending
            ) {
                submittedSessionId = composerSessionId
                commentViewModel.updateCommentDraft(composerSessionId, composerReplyRpid, text, images, sync)
                commentViewModel.sendComment(text, images, sync)
            }
        },
        isSending = comments.isSending, replyToName = comments.replyTarget?.member?.uname,
        inputHint = if (comments.replyTarget == null) comments.rootInputHint else comments.childInputHint,
        canInputComment = comments.canInputComment, emotePackages = emotePackages,
        initialText = composerDraft?.text.orEmpty(),
        initialImageUris = composerDraft?.imageUris.orEmpty(),
        initialSyncToDynamic = composerDraft?.syncToDynamic ?: false,
        onDraftChange = { text, images, sync ->
            commentViewModel.updateCommentDraft(composerSessionId, composerReplyRpid, text, images, sync)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BgmDetailContent(
    state: BgmDetailUiState,
    comments: CommentUiState,
    showVideos: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVideosClick: () -> Unit,
    onVideoClick: (String, Long, String) -> Unit,
    onUserClick: (Long) -> Unit,
    onWish: () -> Unit,
    onShare: () -> Unit,
    onCompose: () -> Unit,
    onReply: (ReplyItem) -> Unit,
    onSort: (CommentSortMode) -> Unit,
    onLoadMore: () -> Unit,
    onCommentClick: (Long, Long) -> Unit,
    onCommentLike: (Long) -> Unit,
    onCommentHate: (Long) -> Unit,
    onCommentDelete: (Long) -> Unit,
    onCommentReport: (Long, Int, String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val detail = state.detail
    val canComment = detail?.musicComment?.oid?.let { it > 0 } == true && detail?.flowAttr?.noComment != true
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(
            title = if (showVideos) "使用音乐的视频" else "音乐详情",
            navigationIcon = { AppIconButton(onClick = onBack) { AppIcon(rememberAppBackIcon(), "返回") } },
            actions = { AppTextButton(onClick = onRetry, enabled = !state.loading && !state.updatingWish) { AppText("刷新") } },
        ) },
        bottomBar = {
            if (detail != null && !showVideos) AppSurface {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (detail.flowAttr?.noShare != true) AppTextButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { AppText("分享") }
                    AppTextButton(onClick = onWish, enabled = !state.updatingWish && !state.loading, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        AppText("${if (detail.wishListen) "已点赞" else "点赞"} ${FormatUtils.formatStat(detail.wishCount.toLong())}",
                            color = if (detail.wishListen) AppContentColors.accent() else AppContentColors.primaryText())
                    }
                }
            }
        },
        floatingActionButton = {
            if (canComment && !showVideos && !state.loading) {
                // 听视频小横条悬浮时上浮避让（与首页/动态页 76dp 预留一致），避免挡住入口
                val nowPlayingBarOverlayVisible by com.android.purebilibili.feature.audio.player
                    .AudioNowPlayingSession.barOverlayVisible
                    .collectAsStateWithLifecycle()
                AppButton(
                    onClick = onCompose,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(bottom = if (nowPlayingBarOverlayVisible) 76.dp else 0.dp),
                ) { AppText("写评论") }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppCircularProgressIndicator() }
            state.error != null -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AppText(state.error)
                AppTextButton(onClick = onRetry) { AppText("重试") }
            }
            detail != null && showVideos -> BgmVideos(detail, state.videos, onVideoClick, Modifier.fillMaxSize().padding(padding))
            detail != null -> BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val wide = maxWidth >= 840.dp
                val commentsEnabled = canComment
                LaunchedEffect(listState, commentsEnabled) {
                    if (commentsEnabled) snapshotFlow {
                        val layout = listState.layoutInfo
                        (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 3
                    }.distinctUntilChanged().collect { if (it) onLoadMore() }
                }
                val commentList: @Composable (Modifier, Boolean) -> Unit = { listModifier, includeDetails ->
                    LazyColumn(modifier = listModifier, state = listState, contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (includeDetails) {
                            item("music-info") { BgmInfoCard(detail, onVideosClick, onVideoClick, onUserClick, Modifier.fillMaxWidth().padding(12.dp)) }
                            item("heat-chart") { BgmHeatChart(detail.hotSongHeat?.songHeat.orEmpty(), Modifier.fillMaxWidth().padding(16.dp)) }
                        }
                        if (commentsEnabled) {
                            item("comment-sort") { CommentSortHeader(comments.replyCount, comments.sortMode, onSort) }
                            items(comments.replies, key = { "reply-${it.rpid}" }) { reply ->
                                ReplyItemView(
                                    item = reply, onClick = { onCommentClick(reply.rpid, 0) },
                                    onSubClick = { root, target -> onCommentClick(root.rpid, target) },
                                    onAvatarClick = { it.toLongOrNull()?.let(onUserClick) },
                                    isLiked = reply.rpid in comments.likedComments,
                                    isHated = reply.rpid in comments.hatedComments,
                                    onLikeClick = { onCommentLike(reply.rpid) }, onHateClick = { onCommentHate(reply.rpid) },
                                    onReplyClick = { onReply(reply) }, onUrlClick = onLinkClick,
                                    onImagePreview = { images, index, _, _ -> previewImages = images; previewIndex = index },
                                    onDeleteClick = if (comments.currentMid > 0 && reply.mid == comments.currentMid) ({ onCommentDelete(reply.rpid) }) else null,
                                    onReportClick = { reason -> onCommentReport(reply.rpid, reason, "") },
                                )
                            }
                            item("comment-status") {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    when {
                                        comments.isRepliesLoading -> AppCircularProgressIndicator()
                                        comments.repliesError != null -> { AppText(comments.repliesError); AppTextButton(onClick = onLoadMore) { AppText("重试") } }
                                        comments.replies.isEmpty() -> {
                                            EmptyState(message = "还没有评论，来聊聊这首音乐吧", enableEasterEgg = false)
                                            AppTextButton(onClick = onCompose, modifier = Modifier.heightIn(min = 48.dp)) { AppText("写评论") }
                                        }
                                        !comments.isRepliesEnd -> AppTextButton(onClick = onLoadMore) { AppText("加载更多") }
                                        else -> AppText("没有更多评论了", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else item("comments-unavailable") { AppText("这首音乐暂未开放评论", modifier = Modifier.padding(24.dp)) }
                    }
                }
                if (wide) Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) {
                        item { BgmInfoCard(detail, onVideosClick, onVideoClick, onUserClick, Modifier.fillMaxWidth().padding(12.dp)) }
                        item { BgmHeatChart(detail.hotSongHeat?.songHeat.orEmpty(), Modifier.fillMaxWidth().padding(16.dp)) }
                    }
                    commentList(Modifier.weight(1f), false)
                } else commentList(Modifier.fillMaxSize(), true)
            }
        }
    }
    if (previewImages.isNotEmpty()) ImagePreviewDialog(images = previewImages, initialIndex = previewIndex, onDismiss = { previewImages = emptyList() })
}

@Composable
private fun BgmInfoCard(
    detail: BgmDetailData,
    onVideosClick: () -> Unit,
    onVideoClick: (String, Long, String) -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var cover by rememberSaveable { mutableStateOf(false) }
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("音乐", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
    AppSurface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSurface(onClick = { cover = true }, modifier = Modifier.size(80.dp), shape = RoundedCornerShape(12.dp)) {
                    AsyncImage(FormatUtils.fixImageUrl(detail.mvCover), "音乐封面", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f)) {
                    AppTextButton(onClick = {
                        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                            putExtra(android.app.SearchManager.QUERY, detail.musicTitle)
                            putExtra(MediaStore.EXTRA_MEDIA_TITLE, detail.musicTitle)
                            putExtra(MediaStore.EXTRA_MEDIA_ARTIST, detail.originArtist.ifBlank { detail.originArtistList })
                            putExtra(MediaStore.EXTRA_MEDIA_ALBUM, detail.album)
                        }
                        try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { copy(detail.musicTitle) }
                    }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        AppText(detail.musicTitle.ifBlank { "未知音乐" }, style = MaterialTheme.typography.titleMedium)
                    }
                    detail.artistsList.forEach { artist ->
                        AppTextButton(onClick = { if (artist.mid > 0) onUserClick(artist.mid) else copy(artist.name) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                            if (artist.face.isNotBlank()) AsyncImage(FormatUtils.fixImageUrl(artist.face), null, modifier = Modifier.size(24.dp))
                            AppText("${artist.identity}：${artist.name}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (detail.musicPublish.isNotBlank()) AppText("发行日期：${detail.musicPublish}", style = MaterialTheme.typography.bodySmall, color = AppSurfaceTokens.onSurfaceVariantSummary())
                }
            }
            SelectionContainer {
                AppText(listOfNotNull(
                    detail.originArtist.ifBlank { detail.originArtistList }.takeIf { it.isNotBlank() }?.let { "原唱：$it" },
                    detail.album.takeIf { it.isNotBlank() }?.let { "专辑：$it" },
                    detail.musicSource.takeIf { it.isNotBlank() }?.let { "出处：$it" },
                ).joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
            }
            val achievements = (detail.achievement + listOf(detail.musicRank, detail.recreationRank)).filter { it.isNotBlank() }.distinct()
            if (achievements.isNotEmpty()) AppText(achievements.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = AppContentColors.accent())
            Row {
                AppTextButton(onClick = { copy(detail.musicTitle) }, modifier = Modifier.heightIn(min = 48.dp)) { AppText("复制歌名") }
                if (detail.mvCid > 0 && (detail.mvBvid.isNotBlank() || detail.mvAid > 0)) AppTextButton(onClick = { onVideoClick(detail.mvBvid.ifBlank { "av${detail.mvAid}" }, detail.mvCid, detail.mvCover) }, modifier = Modifier.heightIn(min = 48.dp)) { AppText("看 MV") }
            }
            AppHorizontalDivider()
            AppText("热歌榜排名", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { AppText(FormatUtils.formatStat(detail.hotSongHeat?.lastHeat ?: detail.musicHot)); AppText("热度", style = MaterialTheme.typography.bodySmall) }
                Column(Modifier.weight(1f)) { AppText(FormatUtils.formatStat(detail.listenPv)); AppText("总播放量", style = MaterialTheme.typography.bodySmall) }
                AppTextButton(onClick = onVideosClick, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                    Column { AppText(FormatUtils.formatStat(detail.musicRelation.toLong())); AppText("使用稿件量 ›", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (cover && detail.mvCover.isNotBlank()) ImagePreviewDialog(
        images = listOf(FormatUtils.fixImageUrl(detail.mvCover)), initialIndex = 0,
        onDismiss = { cover = false },
    )
}

@Composable
private fun BgmVideos(detail: BgmDetailData, videos: List<BgmRecommendVideo>, onVideoClick: (String, Long, String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        AppText("${detail.musicTitle} · 共${videos.size}条视频", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
        if (videos.isEmpty()) AppText("暂无使用这首音乐的视频", modifier = Modifier.padding(24.dp))
        else LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(videos, key = { _, video -> "${video.bvid}:${video.cid}" }) { index, video ->
                Column {
                    ElegantVideoCard(video = VideoItem(id = video.aid.takeIf { it > 0 } ?: video.cid,
                        aid = video.aid, bvid = video.bvid, cid = video.cid, title = video.title, pic = video.cover,
                        owner = Owner(mid = video.mid, name = video.upNickName), stat = Stat(view = video.play, danmaku = video.danmu), duration = video.duration),
                        index = index, animationEnabled = false, glassEnabled = false, blurEnabled = false,
                        onClick = { _, _ -> onVideoClick(video.bvid, video.cid, video.cover) })
                    val labels = (video.labelList.map { it.name } + video.label).filter { it.isNotBlank() }
                    if (labels.isNotEmpty()) AppText(labels.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
