package com.android.purebilibili.feature.video.player

import com.android.purebilibili.feature.download.downloadAudioBackupUrls
import com.android.purebilibili.feature.download.downloadVideoBackupUrls
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.video.ui.components.PlayerNativeMenuTheme
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.PublicVideoNoteItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.VideoNoteRepository
import com.android.purebilibili.data.repository.VideoNoteSavePayload
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.download.DownloadManager
import com.android.purebilibili.feature.download.DownloadQualityDialog
import com.android.purebilibili.feature.download.DownloadStreamUnavailableException
import com.android.purebilibili.feature.download.observeDownloadStreamResolution
import com.android.purebilibili.feature.download.DownloadTask
import com.android.purebilibili.feature.video.note.VideoNoteBlock
import com.android.purebilibili.feature.video.note.VideoNoteContentCodec
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteLoadStatus
import com.android.purebilibili.feature.video.note.VideoNotePublicPreview
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.note.buildVideoNoteShareText
import com.android.purebilibili.feature.video.note.resolveVideoNoteEditableDocument
import com.android.purebilibili.feature.video.ui.section.VideoNoteDeleteConfirmDialog
import com.android.purebilibili.feature.video.ui.section.VideoNoteEditorSheet
import com.android.purebilibili.feature.video.ui.section.VideoNoteListSheet
import com.android.purebilibili.feature.video.usecase.VideoPlaybackUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

data class PiliVideoContentActions(
    val onWatchLater: () -> Unit,
    val onShowNotes: () -> Unit,
    val onDownloadVideo: () -> Unit,
    val onSaveCollectionCover: (String) -> Unit
)

/** Self-hosted actions; never borrow the detail view model's current-video identity. */
@Composable
fun rememberPiliVideoContentActions(
    bvid: String,
    aid: Long,
    cid: Long,
    title: String,
    coverUrl: String,
    pages: List<Page> = emptyList(),
    currentPlayer: Player?,
    isLoggedIn: Boolean
): PiliVideoContentActions = key(bvid, aid, cid, isLoggedIn) {
    rememberPiliVideoContentActionsForIdentity(bvid, aid, cid, title, coverUrl, pages, currentPlayer, isLoggedIn)
}

@Composable
private fun rememberPiliVideoContentActionsForIdentity(
    bvid: String,
    aid: Long,
    cid: Long,
    title: String,
    coverUrl: String,
    pages: List<Page>,
    currentPlayer: Player?,
    isLoggedIn: Boolean
): PiliVideoContentActions {
    val context = LocalContext.current
    // Leaving this keyed composition cancels pending requests before another video can receive their UI.
    val scope = rememberCoroutineScope()
    val player by rememberUpdatedState(currentPlayer)
    val activePages by rememberUpdatedState(pages)
    val activeTitle by rememberUpdatedState(title)
    var metadata by remember { mutableStateOf<ViewInfo?>(null) }
    var noteState by remember { mutableStateOf(VideoNoteUiState()) }
    var showNotes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var publicPage by remember { mutableStateOf(1) }
    var watchLaterBusy by remember { mutableStateOf(false) }
    var downloadBusy by remember { mutableStateOf(false) }
    var qualityOptions by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var showDownload by remember { mutableStateOf(false) }
    var coverToSave by remember { mutableStateOf<String?>(null) }
    var savingCover by remember { mutableStateOf(false) }
    val playbackUseCase = remember { VideoPlaybackUseCase() }

    fun feedback(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            feedback("没有可打开此链接的应用")
        }
    }
    suspend fun targetInfo(): ViewInfo {
        metadata?.let { return it }
        val info = VideoRepository.getVideoInfoOnly(bvid, aid, cid).getOrThrow()
        require(info.bvid == bvid && (aid <= 0L || info.aid == aid)) { "视频信息与当前播放目标不一致，请重新打开视频" }
        require(cid > 0L && (info.cid == cid || info.pages.any { it.cid == cid })) { "找不到当前分 P，无法执行此操作" }
        metadata = info
        return info
    }
    fun share(document: VideoNoteEditorDocument) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, document.title.ifBlank { activeTitle })
            putExtra(Intent.EXTRA_TEXT, buildVideoNoteShareText(activeTitle, bvid, document))
        }
        try {
            context.startActivity(Intent.createChooser(intent, "分享视频笔记"))
        } catch (_: android.content.ActivityNotFoundException) {
            feedback("没有可分享笔记的应用")
        }
    }
    fun loadNotes() {
        if (noteState.status == VideoNoteLoadStatus.LOADING) return
        noteState = noteState.copy(status = VideoNoteLoadStatus.LOADING, errorMessage = null)
        scope.launch {
            try {
                val info = targetInfo()
                val snapshot = VideoNoteRepository.getVideoNoteSnapshot(info.aid).getOrThrow()
                val document = snapshot.privateNote?.let {
                    VideoNoteContentCodec.decode(it.title.ifBlank { info.title }, it.content)
                }
                publicPage = 1
                noteState = noteState.copy(
                    status = VideoNoteLoadStatus.READY,
                    forbidNoteEntrance = snapshot.forbidNoteEntrance,
                    privateNoteId = snapshot.privateNoteId,
                    privateNoteTitle = snapshot.privateNote?.title.orEmpty(),
                    privateNoteSummary = snapshot.privateNote?.summary.orEmpty(),
                    privateNoteDocument = document,
                    publicNoteCount = snapshot.publicNoteTotal,
                    publicNotes = snapshot.publicNotes.map { it.toPiliPreview() },
                    publicNotesEnd = snapshot.publicNotes.size >= snapshot.publicNoteTotal,
                    errorMessage = null
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                noteState = noteState.copy(status = VideoNoteLoadStatus.ERROR, errorMessage = error.message ?: "笔记加载失败")
            }
        }
    }
    val openEditor: () -> Unit = {
        when {
            !isLoggedIn -> feedback("请先登录后再编辑笔记")
            noteState.forbidNoteEntrance -> feedback("该视频暂不支持笔记")
            noteState.status != VideoNoteLoadStatus.READY -> feedback("请等待笔记加载完成，加载失败时请重试")
            else -> {
                showNotes = false
                noteState = noteState.copy(editorVisible = true, editorDocument = resolveVideoNoteEditableDocument(noteState, activeTitle))
            }
        }
    }
    PlayerNativeMenuTheme {
    coverToSave?.let { targetUrl ->
        AppAlertDialog(
            onDismissRequest = { if (!savingCover) coverToSave = null },
            title = { AppText("保存封面") },
            text = { AppText("将此选集封面保存到系统相册？") },
            confirmButton = {
                AppTextButton(enabled = !savingCover, onClick = {
                    savingCover = true
                    scope.launch {
                        try {
                            val saved = DownloadManager.saveImageToGallery(context, targetUrl, activeTitle)
                            feedback(if (saved) "封面已保存到相册" else "封面保存失败，请检查网络和相册权限")
                            coverToSave = null
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            feedback(error.message ?: "封面保存失败")
                        } finally {
                            savingCover = false
                        }
                    }
                }) { AppText(if (savingCover) "保存中" else "保存") }
            },
            dismissButton = {
                AppTextButton(enabled = !savingCover, onClick = { coverToSave = null }) { AppText("取消") }
            }
        )
    }
    VideoNoteListSheet(
        visible = showNotes,
        noteState = noteState,
        isLoggedIn = isLoggedIn,
        onDismiss = { showNotes = false },
        onCreateOrEditClick = openEditor,
        onOfficialEditorClick = {
            if (!isLoggedIn) feedback("请先登录")
            else if (noteState.forbidNoteEntrance) feedback("该视频暂不支持笔记")
            else metadata?.let { openUrl("https://www.bilibili.com/h5/note-app?oid=${it.aid}&pagefrom=ugcvideo") }
                ?: feedback("请等待视频信息加载完成")
        },
        onRetryClick = { loadNotes() },
        onDeleteClick = { if (isLoggedIn) confirmDelete = true else feedback("请先登录") },
        onShareClick = { share(it) },
        onPublicNoteClick = { cvid, url -> openUrl(url.ifBlank { "https://www.bilibili.com/read/cv$cvid" }) },
        onAuthorClick = { mid -> openUrl("https://space.bilibili.com/$mid") },
        onLoadMore = {
            if (!noteState.publicNotesLoadingMore && !noteState.publicNotesEnd) {
                noteState = noteState.copy(publicNotesLoadingMore = true)
                scope.launch {
                    try {
                        val nextPage = publicPage + 1
                        val result = VideoNoteRepository.getPublicVideoNotePage(targetInfo().aid, nextPage).getOrThrow()
                        val combined = (noteState.publicNotes + result.notes.map { it.toPiliPreview() }).distinctBy { it.cvid }
                        publicPage = nextPage
                        noteState = noteState.copy(publicNotes = combined, publicNoteCount = result.total,
                            publicNotesEnd = result.notes.isEmpty() || combined.size >= result.total,
                            publicNotesLoadingMore = false, errorMessage = null)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        noteState = noteState.copy(publicNotesLoadingMore = false, errorMessage = error.message ?: "公开笔记加载失败")
                    }
                }
            }
        }
    )
    VideoNoteEditorSheet(
        noteState = noteState,
        onDismiss = { if (!noteState.saving) noteState = noteState.copy(editorVisible = false) },
        onDocumentChange = { noteState = noteState.copy(editorDocument = it) },
        onTimestampClick = { seconds -> player?.seekTo(seconds.coerceAtLeast(0L) * 1000L) },
        onShare = { share(it) },
        currentTimestampProvider = {
            player?.let { buildPiliVideoNoteTimestamp(cid, activePages.ifEmpty { metadata?.pages.orEmpty() }, it.currentPosition) }
        },
        onSave = { source ->
            if (!isLoggedIn) feedback("请先登录")
            else if (noteState.forbidNoteEntrance) feedback("该视频暂不支持笔记")
            else if (!noteState.saving) {
                val document = source.copy(title = source.title.ifBlank { activeTitle })
                val encoded = VideoNoteContentCodec.encode(document)
                if (encoded.contentLength <= 0) {
                    noteState = noteState.copy(errorMessage = "先写一点内容再保存。")
                } else {
                    noteState = noteState.copy(saving = true, errorMessage = null)
                    scope.launch {
                        try {
                            val id = VideoNoteRepository.savePrivateNote(VideoNoteSavePayload(
                                aid = targetInfo().aid, noteId = noteState.privateNoteId, title = document.title,
                                summary = encoded.summary, content = encoded.content, tags = encoded.tags,
                                contentLength = encoded.contentLength
                            )).getOrThrow()
                            noteState = noteState.copy(privateNoteId = id, privateNoteTitle = document.title,
                                privateNoteSummary = encoded.summary, privateNoteDocument = document,
                                editorDocument = document, saving = false, editorVisible = false, feedbackMessage = "笔记已保存。")
                            showNotes = true
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            noteState = noteState.copy(saving = false, errorMessage = error.message ?: "笔记保存失败")
                        }
                    }
                }
            }
        }
    )
    VideoNoteDeleteConfirmDialog(
        visible = confirmDelete,
        deleting = noteState.deleting,
        onDismiss = { if (!noteState.deleting) confirmDelete = false },
        onConfirm = {
            val id = noteState.privateNoteId
            if (!isLoggedIn) feedback("请先登录")
            else if (id != null && !noteState.deleting) {
                noteState = noteState.copy(deleting = true, errorMessage = null)
                scope.launch {
                    try {
                        VideoNoteRepository.deletePrivateNote(targetInfo().aid, id).getOrThrow()
                        noteState = noteState.copy(privateNoteId = null, privateNoteDocument = null,
                            privateNoteTitle = "", privateNoteSummary = "", editorDocument = VideoNoteEditorDocument(),
                            deleting = false, feedbackMessage = "笔记已删除。")
                        confirmDelete = false
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        noteState = noteState.copy(deleting = false, errorMessage = error.message ?: "笔记删除失败")
                        confirmDelete = false
                    }
                }
            }
        }
    )
    if (showDownload) {
        DownloadQualityDialog(
            title = metadata?.title ?: title,
            qualityOptions = qualityOptions,
            currentQuality = qualityOptions.firstOrNull()?.first ?: 80,
            onDismiss = { showDownload = false },
            onQualitySelected = { quality, options ->
                showDownload = false
                if (!downloadBusy) {
                    downloadBusy = true
                    scope.launch {
                        try {
                            val task = observeDownloadStreamResolution {
                                val info = targetInfo()
                                val data = VideoRepository.getPlayUrlData(bvid, cid, quality, retainOriginalUrls = true)
                                    ?: throw DownloadStreamUnavailableException("无法获取下载地址，请检查登录状态和网络")
                                val selection = playbackUseCase.resolvePlaybackSelection(data, quality)
                                    ?: throw DownloadStreamUnavailableException("当前账号无法下载此画质")
                                if (data.dash?.video?.any { it.id == quality } != true) {
                                    throw DownloadStreamUnavailableException("当前账号无法下载所选画质，请选择其他画质")
                                }
                                val audioUrl = selection.audioUrl.orEmpty()
                                if (selection.videoUrl.isBlank() || audioUrl.isBlank()) {
                                    throw DownloadStreamUnavailableException("未取得完整视频和音频流，无法下载")
                                }
                                val page = info.pages.firstOrNull { it.cid == cid }
                                DownloadTask(
                                    aid = info.aid, bvid = bvid, cid = cid,
                                    title = info.title, episodeLabel = page?.part?.takeIf { info.pages.size > 1 },
                                    cover = info.pic.ifBlank { coverUrl }, ownerName = info.owner.name, ownerFace = info.owner.face,
                                    duration = (page?.duration ?: (data.timelength / 1000L)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                                    quality = quality, qualityDesc = qualityOptions.firstOrNull { it.first == quality }?.second ?: "${quality}P",
                                    videoUrl = selection.videoUrl, audioUrl = audioUrl,
                                    videoBackupUrls = selection.cachedDashVideos.downloadVideoBackupUrls(selection.videoUrl),
                                    audioBackupUrls = selection.cachedDashAudios.downloadAudioBackupUrls(audioUrl),
                                    isVerticalVideo = info.dimension?.isVertical == true, options = options
                                )
                            }
                            feedback(if (DownloadManager.addTask(task)) "已加入下载队列：${task.title}" else "下载任务已存在")
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            feedback(error.message ?: "视频下载失败")
                        } finally {
                            downloadBusy = false
                        }
                    }
                }
            }
        )
    }
    }
    return PiliVideoContentActions(
        onSaveCollectionCover = { url ->
            if (url.isBlank()) feedback("此选集没有可保存的封面")
            else if (!savingCover) coverToSave = url
        },
        onWatchLater = {
            if (!isLoggedIn) feedback("请先登录")
            else if (!watchLaterBusy) {
                watchLaterBusy = true
                scope.launch {
                    try {
                        val targetAid = targetInfo().aid
                        val response = withContext(Dispatchers.IO) { NetworkModule.api.getWatchLaterList() }
                        require(response.code == 0) { response.message.ifBlank { "无法读取稍后再看状态" } }
                        val existing = response.data?.list.orEmpty().any { it.aid == targetAid }
                        val added = ActionRepository.toggleWatchLater(targetAid, !existing).getOrThrow()
                        feedback(if (added) "已加入稍后再看" else "已移出稍后再看")
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        feedback(error.message ?: "稍后再看操作失败")
                    } finally {
                        watchLaterBusy = false
                    }
                }
            }
        },
        onShowNotes = { showNotes = true; loadNotes() },
        onDownloadVideo = {
            if (!isLoggedIn) feedback("请先登录后下载视频")
            else if (!downloadBusy) {
                downloadBusy = true
                scope.launch {
                    try {
                        targetInfo()
                        // 用下载专用查询：允许服务端降级（请求 8K 只有 4K 时就按 4K 列画质），
                        // 否则绝大多数视频都会因为「没有 8K」被判成无法获取下载画质。
                        val data = VideoRepository.getPlayUrlDataForDownload(bvid, cid, 127)
                            ?: error("无法获取视频下载画质，请检查网络或视频权限")
                        val ids = (data.acceptQuality + data.dash?.video.orEmpty().map { it.id }).distinct().sortedDescending()
                        qualityOptions = ids.map { id ->
                            id to (data.acceptDescription.getOrNull(data.acceptQuality.indexOf(id)) ?: "${id}P")
                        }
                        require(qualityOptions.isNotEmpty()) { "此视频暂无可下载画质" }
                        showDownload = true
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        feedback(error.message ?: "下载信息加载失败")
                    } finally {
                        downloadBusy = false
                    }
                }
            }
        }
    )
}

internal fun buildPiliVideoNoteTimestamp(cid: Long, pages: List<Page>, positionMs: Long): VideoNoteBlock.Timestamp? {
    if (cid <= 0L) return null
    val index = pages.indexOfFirst { it.cid == cid }
    if (pages.isNotEmpty() && index < 0) return null
    return VideoNoteBlock.Timestamp(
        seconds = positionMs.coerceAtLeast(0L) / 1000L,
        cid = cid,
        index = index.coerceAtLeast(0),
        cidCount = pages.size.coerceAtLeast(1)
    )
}

private fun PublicVideoNoteItem.toPiliPreview() = VideoNotePublicPreview(
    cvid = cvid, title = title, summary = summary, authorName = author?.name.orEmpty(),
    authorMid = author?.mid ?: 0L, authorFace = author?.face.orEmpty(), authorLevel = author?.level ?: 0,
    authorSenior = (author?.isSeniorMember ?: 0) == 1, pubtime = pubtime, webUrl = webUrl, likes = likes
)
