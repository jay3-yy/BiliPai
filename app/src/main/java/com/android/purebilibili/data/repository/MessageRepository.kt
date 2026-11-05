// 私信数据仓库
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.android.purebilibili.core.store.MessageCenterPreferences
import com.android.purebilibili.app.PureApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * 私信相关数据仓库
 * 负责处理会话列表、私信消息的获取和发送
 */
data class MessageSessionControlInfo(
    val isLimit: Boolean? = null,
    val reportLimit: Boolean? = null,
    val isDnd: Boolean? = null,
    val pushMuted: Boolean? = null,
    val showPushSetting: Boolean = false,
    val followStatus: Int? = null,
    val isIntercept: Boolean? = null,
    val isPinned: Boolean? = null,
)

/** 消息中心总未读 + 四项明细，来自官方消息中心同一条 GetTotalUnread。 */
data class MessageCenterUnread(
    val total: Int,
    val reply: Int,
    val at: Int,
    val like: Int,
    val sysMsg: Int,
)

object MessageRepository {
    private val api = NetworkModule.messageApi
    private val bilibiliApi = NetworkModule.api
    private val _readEvents = MutableSharedFlow<MessageReadEvent>()
    internal val readEvents = _readEvents.asSharedFlow()

    internal suspend fun notifyReadStateChanged(event: MessageReadEvent = MessageReadEvent.Changed) {
        _readEvents.emit(event)
    }

    
    // 设备ID缓存 (用于发送私信)
    private var deviceIdCache: String? = null
    
    /**
     * 获取或生成设备ID (UUID v4格式)
     */
    private fun getDeviceId(): String {
        return deviceIdCache ?: UUID.randomUUID().toString().also {
            deviceIdCache = it
        }
    }

    private fun requireCsrf(): Result<String> {
        val csrf = TokenManager.csrfCache
        return if (csrf.isNullOrEmpty()) {
            Result.failure(Exception("请先登录"))
        } else {
            Result.success(csrf)
        }
    }

    private suspend fun sendMessage(
        receiverId: Long,
        receiverType: Int,
        msgType: Int,
        content: String
    ): Result<SendMessageData> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val senderUid = TokenManager.midCache
            if (senderUid == null || senderUid <= 0) {
                return@withContext Result.failure(Exception("无法获取用户信息，请重新登录"))
            }

            val response = api.sendMsg(
                senderUid = senderUid,
                receiverId = receiverId,
                receiverType = receiverType,
                msgType = msgType,
                content = content,
                timestamp = System.currentTimeMillis() / 1000,
                devId = getDeviceId(),
                csrf = csrf,
                csrfToken = csrf
            )

            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -400 -> "请求参数错误"
                    21007 -> "消息过长，无法发送"
                    21015 -> "需绑定手机号才能发送消息"
                    21020 -> "发送频率过快，请稍后再试"
                    21026 -> "不能给自己发消息"
                    21046 -> "发送过于频繁，请24小时后再试"
                    21047 -> "对方未关注你，最多发送1条消息"
                    25003 -> "对方隐私设置限制，无法发送"
                    25005 -> "已拉黑对方，请先解除拉黑"
                    else -> response.message.ifEmpty { "发送失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "sendMessage exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取未读私信数量
     */
    suspend fun getUnreadCount(): Result<MessageUnreadData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getUnreadCount()
            
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    else -> response.message.ifEmpty { "获取未读数失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "getUnreadCount exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getFeedUnread(): Result<MessageFeedUnreadData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getFeedUnread()
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                val disableLikes = MessageCenterPreferences.disabledLikes(PureApplication.instance).first()
                Result.success(if (disableLikes) checkedResponseData.copy(like = 0) else checkedResponseData)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "获取消息中心未读数失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "getFeedUnread exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 消息中心总未读 + 四项；disabledLikes 开启时从总数里扣掉赞。 */
    suspend fun getMessageCenterUnread(): Result<MessageCenterUnread> = withContext(Dispatchers.IO) {
        MessageImRepository.messageCenterUnread().map { raw ->
            val disableLikes = MessageCenterPreferences.disabledLikes(PureApplication.instance).first()
            val like = if (disableLikes) 0 else raw.like
            MessageCenterUnread(
                total = (raw.total - (raw.like - like)).coerceAtLeast(0),
                reply = raw.reply,
                at = raw.at,
                like = like,
                sysMsg = raw.sysMsg,
            )
        }
    }

    suspend fun getReplyFeed(cursor: Long? = null, cursorTime: Long? = null): Result<MessageFeedReplyData> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getReplyFeed(cursor = cursor, cursorTime = cursorTime)
                if (response.code == 0 && response.data != null) {
                    val checkedResponseData = requireNotNull(response.data)
                    if (cursor == null) notifyReadStateChanged()
                    Result.success(checkedResponseData)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "获取回复消息失败 (${response.code})" }))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MessageRepo", "getReplyFeed exception: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun getAtFeed(cursor: Long? = null, cursorTime: Long? = null): Result<MessageFeedAtData> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getAtFeed(cursor = cursor, cursorTime = cursorTime)
                if (response.code == 0 && response.data != null) {
                    val checkedResponseData = requireNotNull(response.data)
                    if (cursor == null) notifyReadStateChanged()
                    Result.success(checkedResponseData)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "获取@我消息失败 (${response.code})" }))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MessageRepo", "getAtFeed exception: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun getLikeFeed(cursor: Long? = null, cursorTime: Long? = null): Result<MessageFeedLikeData> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getLikeFeed(cursor = cursor, cursorTime = cursorTime)
                if (response.code == 0 && response.data != null) {
                    val checkedResponseData = requireNotNull(response.data)
                    if (cursor == null) notifyReadStateChanged()
                    Result.success(checkedResponseData)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "获取点赞消息失败 (${response.code})" }))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MessageRepo", "getLikeFeed exception: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun getSystemNotices(cursor: Long? = null): Result<List<SystemNoticeItem>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getSystemNotices(cursor = cursor)
                if (response.code == 0) {
                    Result.success(response.data ?: emptyList())
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "获取系统通知失败 (${response.code})" }))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MessageRepo", "getSystemNotices exception: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun updateSystemNoticeCursor(cursor: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val response = api.updateSystemNoticeCursor(csrf = csrf, cursor = cursor)
            if (response.code == 0) {
                notifyReadStateChanged()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "更新系统通知已读失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "updateSystemNoticeCursor exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteFeedItem(type: Int, id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val response = api.deleteFeedItem(type = type, id = id, csrf = csrf, csrfToken = csrf)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "删除消息失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "deleteFeedItem exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun setLikeFeedNotice(id: Long, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val response = api.setFeedNotice(
                id = id,
                noticeState = if (enabled) 0 else 1,
                csrf = csrf,
                csrfToken = csrf
            )
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "更新点赞通知设置失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "setLikeFeedNotice exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取会话列表
     * @param sessionType 会话类型: 1=用户与系统, 2=未关注人, 3=粉丝团, 4=所有, 9=关注的人与系统
     * @param size 返回数量
     */
    suspend fun getSessions(
        sessionType: Int = 4,
        size: Int = 50, //  keep size 50
        page: Int = 1,
        endTs: Long = 0
    ): Result<SessionListData> = withContext(Dispatchers.IO) {
        try {
            com.android.purebilibili.core.util.Logger.d("MessageRepo", "getSessions: type=$sessionType, size=$size, page=$page")
            
            val response = api.getSessions(
                sessionType = sessionType,
                size = size,
                pn = page, // Revert to using dynamic page
                endTs = endTs,
                unfollowFold = 0
            )
            
            com.android.purebilibili.core.util.Logger.d("MessageRepo", "getSessions response: code=${response.code}, sessions=${response.data?.session_list?.size ?: 0}")
            
            // [Debug] 打印第一个会话的详细信息以调试字段结构
            response.data?.session_list?.firstOrNull()?.let { first ->
                android.util.Log.d("MessageRepo", "First session debug: talker_id=${first.talker_id}, " +
                    "account_info=${first.account_info}, " +
                    "name=${first.account_info?.name}, " +
                    "pic_url=${first.account_info?.pic_url}, " +
                    "face=${first.account_info?.face}, " +
                    "avatarUrl=${first.account_info?.avatarUrl}")
            }
            
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -400 -> "请求参数错误"
                    else -> response.message.ifEmpty { "获取会话列表失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "getSessions exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取私信消息记录
     * @param talkerId 聊天对象ID
     * @param sessionType 会话类型: 1=用户, 2=粉丝团
     * @param size 返回消息数量
     * @param endSeqno 结束序列号 (用于分页加载更早的消息)
     */
    suspend fun getMessages(
        talkerId: Long,
        sessionType: Int = 1,
        size: Int = 20,
        endSeqno: Long = 0
    ): Result<MessageHistoryData> = withContext(Dispatchers.IO) {
        try {
            com.android.purebilibili.core.util.Logger.d("MessageRepo", "getMessages: talkerId=$talkerId, type=$sessionType, size=$size")
            
            val response = api.fetchSessionMsgs(
                talkerId = talkerId,
                sessionType = sessionType,
                size = size,
                endSeqno = endSeqno
            )
            
            com.android.purebilibili.core.util.Logger.d("MessageRepo", "getMessages response: code=${response.code}, messages=${response.data?.messages?.size ?: 0}")
            
            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -400 -> "请求参数错误"
                    else -> response.message.ifEmpty { "获取消息记录失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "getMessages exception: ${e.message}", e)
            val message = when (e) {
                is kotlinx.serialization.SerializationException -> "消息数据暂时无法读取，请稍后重试"
                is java.io.IOException -> "网络连接失败，请检查网络后重试"
                else -> "消息加载失败，请稍后重试"
            }
            Result.failure(Exception(message, e))
        }
    }

    /** 会话自身的 max_seqno，用作已读 ack 的边界。 */
    suspend fun getSessionMaxSeqno(talkerId: Long, sessionType: Int): Long = withContext(Dispatchers.IO) {
        runCatching {
            api.getSessionDetail(talkerId = talkerId, sessionType = sessionType)
                .takeIf { it.code == 0 }
                ?.data
                ?.max_seqno
                ?: 0L
        }.getOrDefault(0L)
    }

    suspend fun getSessionControlInfo(
        talkerId: Long,
        sessionType: Int
    ): Result<MessageSessionControlInfo> = withContext(Dispatchers.IO) {
        try {
            val detail = runCatching {
                api.getSessionDetail(talkerId = talkerId, sessionType = sessionType)
                    .takeIf { it.code == 0 }
                    ?.data
            }.getOrNull()

            val ownUid = TokenManager.midCache
            val dnd = if (ownUid != null && ownUid > 0) {
                runCatching {
                    api.getMsgDnd(
                        ownUid = ownUid,
                        uid = talkerId.takeIf { sessionType == 1 },
                        groupId = talkerId.takeIf { sessionType == 2 }
                    ).takeIf { it.code == 0 }?.data
                }.getOrNull()
            } else {
                null
            }
            val isDnd = when (sessionType) {
                2 -> dnd?.group_settings?.firstOrNull { it.id == talkerId }?.setting == 1
                else -> dnd?.uid_settings?.firstOrNull { it.id == talkerId }?.setting == 1
            }.takeIf { dnd != null } ?: detail?.is_dnd?.let { it == 1 }

            val limit = if (sessionType == 1) {
                runCatching {
                    api.getSessionLimit(uid = talkerId)
                        .takeIf { it.code == 0 }
                        ?.data
                }.getOrNull()
            } else {
                null
            }
            val push = if (sessionType == 1) {
                runCatching {
                    api.getSessionPushSetting(talkerUid = talkerId)
                        .takeIf { it.code == 0 }
                        ?.data
                }.getOrNull()
            } else {
                null
            }

            Result.success(
                MessageSessionControlInfo(
                    isLimit = limit?.let { it.is_limit == 1 },
                    reportLimit = limit?.let { it.report_limit == 1 },
                    isDnd = isDnd,
                    pushMuted = push?.let { it.push_setting == 1 },
                    showPushSetting = push?.show_push_setting == 1,
                    followStatus = push?.follow_status,
                    isIntercept = detail?.is_intercept?.let { it == 1 },
                    isPinned = detail?.top_ts?.let { it > 0 },
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "getSessionControlInfo exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun setSessionDnd(
        talkerId: Long,
        sessionType: Int,
        enabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = requireCsrf().getOrElse { return@withContext Result.failure(it) }
            val response = api.setMsgDnd(
                ownUid = TokenManager.midCache,
                setting = if (enabled) 1 else 0,
                dndUid = talkerId.takeIf { sessionType == 1 },
                dndGroupId = talkerId.takeIf { sessionType == 2 },
                csrf = csrf,
                csrfToken = csrf
            )
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "更新免打扰失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "setSessionDnd exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun setSessionPushMuted(
        talkerId: Long,
        muted: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = requireCsrf().getOrElse { return@withContext Result.failure(it) }
            val response = api.setPushSetting(
                talkerUid = talkerId,
                setting = if (muted) 1 else 0,
                csrf = csrf,
                csrfToken = csrf
            )
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "更新推送设置失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "setSessionPushMuted exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun setSessionIntercept(
        talkerId: Long,
        intercepted: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = requireCsrf().getOrElse { return@withContext Result.failure(it) }
            val response = api.updateIntercept(
                talkerId = talkerId,
                status = if (intercepted) 1 else 0,
                csrf = csrf,
                csrfToken = csrf
            )
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "更新拦截状态失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "setSessionIntercept exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun markDustbinRead(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = requireCsrf().getOrElse { return@withContext Result.failure(it) }
            val response = api.batchUpdateDustbinAck(csrf = csrf, csrfToken = csrf)
            if (response.code == 0) {
                notifyReadStateChanged()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "拦截会话已读失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "markDustbinRead exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun clearDustbinSessions(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = requireCsrf().getOrElse { return@withContext Result.failure(it) }
            val response = api.batchRemoveDustbin(csrf = csrf, csrfToken = csrf)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "清空拦截会话失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "clearDustbinSessions exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 发送文字私信
     * @param receiverId 接收者ID
     * @param content 消息内容
     * @param receiverType 接收者类型: 1=用户, 2=粉丝团
     */
    suspend fun sendTextMessage(
        receiverId: Long,
        content: String,
        receiverType: Int = 1
    ): Result<SendMessageData> = withContext(Dispatchers.IO) {
        try {
            com.android.purebilibili.core.util.Logger.d("MessageRepo", "sendTextMessage: to=$receiverId, content=$content")

            sendMessage(
                receiverId = receiverId,
                receiverType = receiverType,
                msgType = 1,
                content = MessageSendPayloadFactory.buildTextContent(content)
            ).also { result ->
                result.onSuccess { data ->
                    com.android.purebilibili.core.util.Logger.d("MessageRepo", "sendTextMessage success: msgKey=${data.msg_key}")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "sendTextMessage exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendShareMessage(
        receiverId: Long,
        receiverType: Int,
        content: String,
    ): Result<SendMessageData> = sendMessage(
        receiverId = receiverId,
        receiverType = receiverType,
        msgType = 7,
        content = content,
    )

    suspend fun withdrawMessage(
        receiverId: Long,
        msgKey: Long,
        receiverType: Int = 1
    ): Result<SendMessageData> = withContext(Dispatchers.IO) {
        try {
            com.android.purebilibili.core.util.Logger.d("MessageRepo", "withdrawMessage: to=$receiverId, msgKey=$msgKey")

            sendMessage(
                receiverId = receiverId,
                receiverType = receiverType,
                msgType = 5,
                content = MessageSendPayloadFactory.buildWithdrawContent(msgKey)
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "withdrawMessage exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadPrivateImage(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<UploadCommentImageData> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val mediaType = mimeType.toMediaType()
            val fileBody = bytes.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData(
                "file_up",
                fileName.ifBlank { "message_image.jpg" },
                fileBody
            )
            val textMedia = "text/plain".toMediaType()
            val bizBody = "im".toRequestBody(textMedia)
            val csrfBody = csrf.toRequestBody(textMedia)

            val response = bilibiliApi.uploadPrivateMessageImage(
                fileUp = part,
                biz = bizBody,
                csrf = csrfBody
            )

            if (response.code == 0 && response.data != null) {
                val checkedResponseData = requireNotNull(response.data)
                Result.success(checkedResponseData)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "图片上传失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "uploadPrivateImage exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendImageMessage(
        receiverId: Long,
        imageUrl: String,
        width: Int,
        height: Int,
        imageType: String,
        size: Float,
        receiverType: Int = 1
    ): Result<SendMessageData> = withContext(Dispatchers.IO) {
        try {
            sendMessage(
                receiverId = receiverId,
                receiverType = receiverType,
                msgType = 2,
                content = MessageSendPayloadFactory.buildImageContent(
                    imageUrl = imageUrl,
                    width = width,
                    height = height,
                    imageType = imageType,
                    size = size
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "sendImageMessage exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /** 标记会话已读：官方 ImInterface/UpdateAck 优先，失败回退 Web update_ack。 */
    suspend fun markAsRead(
        talkerId: Long,
        sessionType: Int,
        ackSeqno: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val grpc = MessageImRepository.updateAck(sessionType, talkerId, ackSeqno)
            if (grpc.isSuccess) {
                notifyReadStateChanged(MessageReadEvent.PrivateSession(talkerId, sessionType, ackSeqno))
                return@withContext Result.success(Unit)
            }
            com.android.purebilibili.core.util.Logger.w(
                "MessageRead",
                "gRPC UpdateAck failed: ${grpc.exceptionOrNull()?.message}, fallback REST",
            )

            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.updateAck(
                talkerId = talkerId,
                sessionType = sessionType,
                ackSeqno = ackSeqno,
                csrf = csrf,
                csrfToken = csrf
            )
            
            if (response.code == 0) {
                notifyReadStateChanged(MessageReadEvent.PrivateSession(talkerId, sessionType, ackSeqno))
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "标记已读失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "markAsRead exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 置顶/取消置顶会话
     * @param talkerId 聊天对象ID
     * @param sessionType 会话类型
     * @param setTop true=置顶, false=取消置顶
     */
    suspend fun setSessionTop(
        talkerId: Long,
        sessionType: Int,
        setTop: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.setTop(
                talkerId = talkerId,
                sessionType = sessionType,
                opType = if (setTop) 0 else 1,  // 0=置顶, 1=取消置顶
                csrf = csrf,
                csrfToken = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "操作失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "setSessionTop exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 移除会话
     * @param talkerId 聊天对象ID
     * @param sessionType 会话类型
     */
    suspend fun removeSession(
        talkerId: Long,
        sessionType: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.removeSession(
                talkerId = talkerId,
                sessionType = sessionType,
                csrf = csrf,
                csrfToken = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "移除会话失败" }))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MessageRepo", "removeSession exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
