// 聊天详情 ViewModel
package com.android.purebilibili.feature.message

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.EmoteInfo
import com.android.purebilibili.data.model.response.PrivateMessageItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.MessageSessionControlInfo
import com.android.purebilibili.data.repository.MessageManagementRepository
import com.android.purebilibili.data.model.response.EmotePackage
import com.android.purebilibili.data.repository.MessageRepository
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_PRIVATE_IMAGE_BYTES = 15 * 1024 * 1024

/**
 * 视频预览信息 (用于链接预览)
 */
data class VideoPreviewInfo(
    val bvid: String,
    val title: String,
    val cover: String,
    val ownerName: String,
    val viewCount: Long,
    val danmakuCount: Long,
    val duration: Long = 0
)

data class ChatUiState(
    val accountIdentity: AccountSessionIdentity = TokenManager.accountIdentity.value,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isUploadingImage: Boolean = false,
    val isLoadingMore: Boolean = false,
    val withdrawingMessageKey: Long? = null,
    val messages: List<PrivateMessageItem> = emptyList(),
    val emoteInfos: List<EmoteInfo> = emptyList(),
    val hasMore: Boolean = false,
    val minSeqno: Long = 0,
    val messagesLoaded: Boolean = false,
    val scrollToLatestVersion: Long = 0L,
    val error: String? = null,
    val refreshError: String? = null,
    val loadMoreError: String? = null,
    val sendError: String? = null,
    val sentText: String? = null,
    val notice: String? = null,
    val emotePackages: List<EmotePackage> = emptyList(),
    val isEmoteLoading: Boolean = false,
    val emoteError: String? = null,
    val userInfo: UserBasicInfo? = null,
    val isReporting: Boolean = false,
    val reportError: String? = null,
    val reportSuccessVersion: Long = 0,
    val sessionControlInfo: MessageSessionControlInfo = MessageSessionControlInfo(),
    val isSessionControlLoading: Boolean = false,
    val isSessionControlUpdating: Boolean = false,
    val videoPreviews: Map<String, VideoPreviewInfo> = emptyMap()  // bvid -> VideoPreviewInfo
)

class ChatViewModel(
    private val talkerId: Long,
    private val sessionType: Int
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var messageLoadMutex = Mutex()
    private var readAckMutex = Mutex()
    private var latestMessagesJob: Job? = null
    private var lastReadSeqno = 0L
    
    // 视频预览缓存
    private val videoPreviewCache = mutableMapOf<String, VideoPreviewInfo>()
    
    // 正在加载的 bvid 集合 (防止重复请求)
    private val loadingBvids = mutableSetOf<String>()
    
    // 当前用户 mid
    val currentUserMid: Long
        get() = TokenManager.midCache ?: 0
    
    // BV号匹配正则
    private val bvPattern = Regex("BV[a-zA-Z0-9]{10}")
    private val avPattern = Regex("av(\\d+)", RegexOption.IGNORE_CASE)
    
    init {
        accountRequests.observe {
            latestMessagesJob = null
            messageLoadMutex = Mutex()
            readAckMutex = Mutex()
            lastReadSeqno = 0L
            videoPreviewCache.clear()
            loadingBvids.clear()
            _uiState.value = ChatUiState(accountIdentity = accountRequests.identity)
            loadMessages()
            loadSessionControlInfo()
        }
        loadMessages()
        loadSessionControlInfo()
    }
    
    /**
     * 加载消息
     */
    fun loadMessages(scrollToLatest: Boolean = false) {
        if (latestMessagesJob?.isActive == true && !scrollToLatest) return
        latestMessagesJob = accountRequests.launch {
            loadLatestMessages(showLoading = true, scrollToLatest = scrollToLatest)
        }
    }

    /** Called by the visible screen; cancellation stops the current refresh too. */
    suspend fun refreshMessages() {
        if (!_uiState.value.messagesLoaded || latestMessagesJob?.isActive == true) return
        loadLatestMessages(showLoading = false, scrollToLatest = false)
    }

    private suspend fun loadLatestMessages(showLoading: Boolean, scrollToLatest: Boolean) {
        val requestedIdentity = accountRequests.identity
        val requestMutex = messageLoadMutex
        requestMutex.withLock {
            if (!accountRequests.isCurrent(requestedIdentity)) return@withLock
            if (showLoading) {
                _uiState.update {
                    it.copy(isLoading = !it.messagesLoaded, error = null, refreshError = null)
                }
            }
            try {
                val data = MessageRepository.getMessages(
                    talkerId = talkerId,
                    sessionType = sessionType,
                    size = 30,
                ).getOrThrow()
                if (!accountRequests.isCurrent(requestedIdentity)) return@withLock
                var incoming = data.messages.orEmpty().reversed()
                var emotes = data.e_infos.orEmpty()
                val existingKeys = _uiState.value.messages.map(::chatMessageKey).toSet()
                var gapCursor = data.min_seqno
                var gapHasMore = data.has_more == 1
                // On resume, fill the gap if more than one page arrived while the screen was away.
                while (
                    existingKeys.isNotEmpty() && incoming.isNotEmpty() && gapHasMore && gapCursor > 0L &&
                    incoming.none { chatMessageKey(it) in existingKeys }
                ) {
                    if (!accountRequests.isCurrent(requestedIdentity)) return@withLock
                    val gapPage = MessageRepository.getMessages(
                        talkerId = talkerId,
                        sessionType = sessionType,
                        size = 30,
                        endSeqno = gapCursor,
                    ).getOrThrow()
                    val older = gapPage.messages.orEmpty().reversed()
                    incoming = mergeChatMessages(older, incoming)
                    emotes = emotes + gapPage.e_infos.orEmpty()
                    gapHasMore = older.isNotEmpty() && gapPage.has_more == 1 &&
                        gapPage.min_seqno in 1L until gapCursor
                    gapCursor = gapPage.min_seqno
                }
                if (!accountRequests.isCurrent(requestedIdentity)) return@withLock
                _uiState.update { current ->
                    val replaceCursor = current.messages.isEmpty()
                    current.copy(
                        isLoading = false,
                        messagesLoaded = true,
                        messages = mergeChatMessages(current.messages, incoming),
                        emoteInfos = (current.emoteInfos + emotes).distinctBy { it.text },
                        hasMore = if (replaceCursor) {
                            incoming.isNotEmpty() && data.has_more == 1 && data.min_seqno > 0L
                        } else current.hasMore,
                        minSeqno = if (replaceCursor) data.min_seqno else current.minSeqno,
                        error = null,
                        refreshError = null,
                        scrollToLatestVersion = current.scrollToLatestVersion + if (scrollToLatest) 1L else 0L,
                        videoPreviews = videoPreviewCache.toMap(),
                    )
                }
                // ack 边界用会话 max_seqno，历史页的 seqno 可能不够。
                val readSeqno = maxOf(data.max_seqno, resolveChatReadSeqno(data.messages.orEmpty()) ?: 0L)
                if (readSeqno > 0L) markAsRead(readSeqno)
                scanAndLoadVideoPreviews(incoming)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!accountRequests.isCurrent(requestedIdentity)) return@withLock
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = (e.message ?: "加载消息失败").takeUnless { current.messagesLoaded },
                        refreshError = (e.message ?: "刷新消息失败").takeIf { current.messagesLoaded },
                    )
                }
            }
        }
    }
    
    /**
     * 扫描消息中的视频链接并预加载
     */
    private fun scanAndLoadVideoPreviews(messages: List<PrivateMessageItem>) {
        val bvids = mutableSetOf<String>()
        
        messages.forEach { msg ->
            if (msg.msg_type == 1) {
                val content = parseTextContent(msg.content)
                // 查找 BV 号
                bvPattern.findAll(content).forEach { match ->
                    bvids.add(match.value)
                }
            }
        }
        
        // 加载未缓存的视频信息
        bvids.forEach { bvid ->
            loadVideoPreview(bvid)
        }
    }
    
    /**
     * 加载单个视频预览信息
     */
    fun loadVideoPreview(bvid: String) {
        // 已缓存或正在加载则跳过
        if (videoPreviewCache.containsKey(bvid) || loadingBvids.contains(bvid)) {
            return
        }
        
        loadingBvids.add(bvid)
        
        accountRequests.launch {
            try {
                val result = VideoRepository.getVideoDetails(bvid)
                result.onSuccess { (viewInfo, _) ->
                    val preview = VideoPreviewInfo(
                        bvid = viewInfo.bvid,
                        title = viewInfo.title,
                        cover = viewInfo.pic,
                        ownerName = viewInfo.owner.name,
                        viewCount = viewInfo.stat.view.toLong(),
                        danmakuCount = viewInfo.stat.danmaku.toLong(),
                        duration = viewInfo.pages.firstOrNull()?.duration ?: 0
                    )
                    videoPreviewCache[bvid] = preview
                    
                    // 更新 UI
                    _uiState.value = _uiState.value.copy(
                        videoPreviews = videoPreviewCache.toMap()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "Failed to load video preview for $bvid", e)
            } finally {
                loadingBvids.remove(bvid)
            }
        }
    }
    
    /**
     * 解析文本消息内容
     */
    /**
     * 解析文本消息内容
     * 增强版: 处理非JSON内容，以及提取 "content" 字段
     */
    private fun parseTextContent(content: String): String {
        if (content.isBlank()) return ""
        
        // 如果不是 JSON 格式 (不以 { 开头)，直接返回
        if (!content.trim().startsWith("{")) {
            return content
        }

        return try {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(content)
            
            // 尝试提取 "content" 字段
            if (element is kotlinx.serialization.json.JsonObject) {
                 element["content"]?.let { 
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString()
                 } ?: content // 如果没有 content 字段，返回原字符串
            } else {
                content
            }
        } catch (e: Exception) {
            // 解析失败，说明不是合法JSON，直接返回原文
            content
        }
    }
    
    /**
     * 加载更多历史消息
     */
    fun loadMoreMessages() {
        val current = _uiState.value
        if (current.isLoadingMore || current.isLoading || !current.hasMore || current.minSeqno <= 0L) return
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        accountRequests.launch {
            try {
                messageLoadMutex.withLock {
                    val cursor = _uiState.value.minSeqno
                    val data = MessageRepository.getMessages(
                        talkerId = talkerId,
                        sessionType = sessionType,
                        size = 30,
                        endSeqno = cursor,
                    ).getOrThrow()
                    val older = data.messages.orEmpty().reversed()
                    _uiState.update {
                        it.copy(
                            messages = mergeChatMessages(older, it.messages),
                            emoteInfos = (it.emoteInfos + data.e_infos.orEmpty()).distinctBy { emote -> emote.text },
                            hasMore = older.isNotEmpty() && data.has_more == 1 && data.min_seqno in 1L until cursor,
                            minSeqno = data.min_seqno,
                            loadMoreError = null,
                        )
                    }
                    scanAndLoadVideoPreviews(older)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loadMoreError = e.message ?: "加载历史消息失败") }
            } finally {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
    
    /**
     * 发送文字消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isSending || _uiState.value.isUploadingImage) return
        _uiState.update { it.copy(isSending = true, sendError = null) }
        
        accountRequests.launch {
            try {
                MessageRepository.sendTextMessage(
                    receiverId = talkerId,
                    content = content.trim(),
                    receiverType = sessionType,
                ).getOrThrow()
                _uiState.update { it.copy(sentText = content) }
                loadMessages(scrollToLatest = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(sendError = e.message ?: "发送失败") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun consumeSentText(text: String) {
        _uiState.update { if (it.sentText == text) it.copy(sentText = null) else it }
    }

    fun sendImageMessage(context: Context, imageUri: Uri) {
        if (_uiState.value.isUploadingImage || _uiState.value.isSending) return

        accountRequests.launch {
            _uiState.value = _uiState.value.copy(isUploadingImage = true, sendError = null)

            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = readPrivateImageBytes(context, imageUri)

                    if (bytes.isEmpty()) {
                        error("图片内容为空")
                    }

                    val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                    val fileName = queryDisplayName(context, imageUri)
                        ?: "message_${System.currentTimeMillis()}.jpg"

                    val uploadData = MessageRepository.uploadPrivateImage(
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes
                    ).getOrElse { throw it }

                    MessageRepository.sendImageMessage(
                        receiverId = talkerId,
                        imageUrl = uploadData.imageUrl,
                        width = uploadData.imageWidth,
                        height = uploadData.imageHeight,
                        imageType = mimeType.substringAfter('/', "jpg"),
                        size = uploadData.imgSize,
                        receiverType = sessionType
                    ).getOrElse { throw it }
                }
            }.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isUploadingImage = false)
                    loadMessages(scrollToLatest = true)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isUploadingImage = false,
                        sendError = error.message ?: "图片发送失败"
                    )
                }
            )
        }
    }

    fun loadSessionControlInfo() {
        accountRequests.launch {
            _uiState.value = _uiState.value.copy(isSessionControlLoading = true)
            MessageRepository.getSessionControlInfo(talkerId, sessionType)
                .onSuccess { info ->
                    _uiState.value = _uiState.value.copy(
                        sessionControlInfo = info,
                        isSessionControlLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isSessionControlLoading = false)
                }
        }
    }

    fun loadEmotes() {
        if (_uiState.value.isEmoteLoading || _uiState.value.emotePackages.isNotEmpty()) return
        _uiState.update { it.copy(isEmoteLoading = true, emoteError = null) }
        accountRequests.launch {
            MessageManagementRepository.emotes().fold(
                onSuccess = { packages -> _uiState.update { it.copy(isEmoteLoading = false, emotePackages = packages) } },
                onFailure = { error -> _uiState.update { it.copy(isEmoteLoading = false, emoteError = error.message ?: "加载表情失败") } },
            )
        }
    }
    fun loadChatUser(fallbackName: String = "") {
        if (sessionType != 1) return
        val cached = MessageUserInfoLoader.cached(talkerId)
        val initial = InboxUserInfoResolver.mergeFetchedUserInfo(
            UserBasicInfo(talkerId, fallbackName, ""),
            cached,
        )
        _uiState.update { it.copy(userInfo = InboxUserInfoResolver.mergeFetchedUserInfo(it.userInfo, initial)) }
        accountRequests.launch {
            val user = MessageUserInfoLoader.fetch(talkerId)
            _uiState.update { it.copy(userInfo = InboxUserInfoResolver.mergeFetchedUserInfo(it.userInfo, user)) }
        }
    }
    fun togglePinned() {
        val pinned = _uiState.value.sessionControlInfo.isPinned ?: return
        updateSessionControl { MessageRepository.setSessionTop(talkerId, sessionType, !pinned) }
    }
    fun toggleBlocked() {
        val blocked = _uiState.value.sessionControlInfo.followStatus == 128
        updateSessionControl { MessageManagementRepository.block(talkerId, !blocked) }
    }
    fun clearReportError() { _uiState.update { it.copy(reportError = null) } }
    fun report(messageKey: Long?, reason: Int, description: String, accusedUid: Long = talkerId) {
        if (_uiState.value.isReporting) return
        _uiState.update { it.copy(isReporting = true, reportError = null) }
        accountRequests.launch {
            MessageManagementRepository.report(accusedUid, messageKey, reason, description).fold(
                onSuccess = { _uiState.update { it.copy(isReporting = false, reportSuccessVersion = it.reportSuccessVersion + 1, notice = "举报已提交") } },
                onFailure = { error -> _uiState.update { it.copy(isReporting = false, reportError = error.message ?: "举报失败") } },
            )
        }
    }

    fun toggleDnd() {
        val current = _uiState.value.sessionControlInfo.isDnd == true
        updateSessionControl {
            MessageRepository.setSessionDnd(talkerId, sessionType, !current)
        }
    }

    fun togglePushMuted() {
        if (sessionType != 1) return
        val current = _uiState.value.sessionControlInfo.pushMuted == true
        updateSessionControl {
            MessageRepository.setSessionPushMuted(talkerId, !current)
        }
    }

    fun toggleIntercept() {
        if (sessionType != 1) return
        val current = _uiState.value.sessionControlInfo.isIntercept == true
        updateSessionControl {
            MessageRepository.setSessionIntercept(talkerId, !current)
        }
    }

    private fun updateSessionControl(action: suspend () -> Result<Unit>) {
        if (_uiState.value.isSessionControlUpdating) return
        accountRequests.launch {
            _uiState.value = _uiState.value.copy(isSessionControlUpdating = true, sendError = null)
            action().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSessionControlUpdating = false)
                    loadSessionControlInfo()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSessionControlUpdating = false,
                        sendError = error.message ?: "更新会话设置失败"
                    )
                }
            )
        }
    }

    fun withdrawMessage(message: PrivateMessageItem) {
        if (message.msg_key <= 0L || message.msg_status == 1 || _uiState.value.withdrawingMessageKey != null) return

        accountRequests.launch {
            _uiState.value = _uiState.value.copy(
                withdrawingMessageKey = message.msg_key,
                sendError = null
            )

            MessageRepository.withdrawMessage(
                receiverId = talkerId,
                msgKey = message.msg_key,
                receiverType = sessionType
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        withdrawingMessageKey = null,
                        messages = ChatMessageMutationPolicy.markWithdrawn(
                            messages = _uiState.value.messages,
                            msgKey = message.msg_key
                        )
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        withdrawingMessageKey = null,
                        sendError = e.message ?: "撤回失败"
                    )
                }
            )
        }
    }
    
    /**
     * 标记为已读
     */
    private fun markAsRead(seqno: Long) {
        if (seqno <= lastReadSeqno) return
        val requestedIdentity = accountRequests.identity
        val requestMutex = readAckMutex
        accountRequests.launch {
            requestMutex.withLock {
                if (!accountRequests.isCurrent(requestedIdentity) || seqno <= lastReadSeqno) return@withLock
                // 再用会话自身的 max_seqno 收口。
                val detailMax = MessageRepository.getSessionMaxSeqno(talkerId, sessionType)
                val targetSeqno = maxOf(seqno, detailMax)
                if (targetSeqno <= lastReadSeqno) return@withLock
                val result = MessageRepository.markAsRead(talkerId, sessionType, targetSeqno)
                if (!accountRequests.isCurrent(requestedIdentity)) return@withLock
                result.onSuccess {
                    lastReadSeqno = maxOf(lastReadSeqno, targetSeqno)
                }.onFailure { error ->
                    _uiState.update { it.copy(refreshError = "标记已读失败：${error.message ?: "请稍后重试"}") }
                }
            }
        }
    }
    
    /**
     * 清除发送错误
     */
    fun clearSendError() {
        _uiState.value = _uiState.value.copy(sendError = null, notice = null)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
    }

    private fun readPrivateImageBytes(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val declaredSize = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getLong(index)
                    } else null
                }
        }.getOrNull()
        if (declaredSize != null && declaredSize > MAX_PRIVATE_IMAGE_BYTES) {
            error("图片过大（单张最大 15MB）")
        }

        return resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_PRIVATE_IMAGE_BYTES) {
                    error("图片过大（单张最大 15MB）")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取图片文件")
    }
    
    /**
     * 工厂类
     */
    class Factory(
        private val talkerId: Long,
        private val sessionType: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(talkerId, sessionType) as T
        }
    }
}
