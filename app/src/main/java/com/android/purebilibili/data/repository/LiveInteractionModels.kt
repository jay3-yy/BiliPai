package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.EmoticonPkg

data class LivePagedResult<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val nextPage: Int,
    val totalCount: Int = 0
)

data class LiveDanmakuSendRequest(
    val roomId: Long,
    val message: String,
    val color: Int = 16777215,
    val fontSize: Int = 25,
    val mode: Int = 1,
    val bubble: Int = 0,
    val roomType: Int = 0,
    val jumpFrom: Int = 0,
    val replyMid: Long = 0,
    val replyAttr: Int = 0,
    val replyUname: String = "",
    val replayDmid: String = "",
    val statistics: String = """{"appId":100,"platform":5}""",
    val dmType: Int? = null,
    val emoticonOptions: String? = null
)

data class LiveDanmakuReportRequest(
    val roomId: Long,
    val uid: Long,
    val uname: String,
    val message: String,
    val dmid: String,
    val reportTime: Long,
    val sign: String,
    val reason: LiveReportReason,
    val dmType: Int = 0
)

data class LiveSuperChatReportRequest(
    val roomId: Long,
    val uid: Long,
    val uname: String,
    val message: String,
    val messageId: Long,
    val token: String,
    val reportTime: Long,
    val reason: LiveReportReason
)

data class LiveReportReason(
    val id: Int,
    val label: String,
    val apiReason: String
)

data class LiveShieldInfo(
    val level: Int = 0,
    val medal: Int = 0,
    val verify: Int = 0,
    // [新增] 非正式会员 / 未绑定手机 屏蔽规则（0=关，>0=开）
    val rank: Int = 0,
    val phone: Int = 0,
    val keywords: List<LiveShieldKeyword> = emptyList(),
    val users: List<LiveShieldUser> = emptyList()
)

data class LiveShieldKeyword(
    val id: Long = 0,
    val keyword: String
)

data class LiveShieldUser(
    val uid: Long,
    val uname: String = "",
    val face: String = "",
    val id: Long = 0
)

data class LiveEmoticonPackage(
    val id: Int,
    val name: String,
    val items: List<LiveEmoticonItem>,
    val pkgType: Int = 0
)

data class LiveEmoticonItem(
    val emoji: String,
    val url: String,
    val description: String = "",
    // 独立大表情使用此 ID；普通表情发送 emoji 文本。
    val emoticonUnique: String = "",
    val dmType: Int = 1,
    val width: Int = 80,
    val height: Int = 80,
) {
    val displayText: String get() = emoji.ifBlank { description.ifBlank { "表情" } }
}

fun EmoticonPkg.toLiveEmoticonPackage(): LiveEmoticonPackage = LiveEmoticonPackage(
    id = pkg_id,
    name = pkg_name.ifBlank { "表情" },
    pkgType = pkg_type,
    items = emoticons.orEmpty().mapNotNull { emotion ->
        if (emotion.emoji.isBlank() && emotion.url.isBlank()) return@mapNotNull null
        LiveEmoticonItem(
            emoji = emotion.emoji,
            url = emotion.url,
            description = emotion.des,
            emoticonUnique = emotion.emoticon_unique,
            dmType = if (pkg_type == 3) 0 else 1,
            width = emotion.width?.takeIf { it > 0 } ?: 80,
            height = emotion.height?.takeIf { it > 0 } ?: 80
        )
    }
)

fun buildLiveEmoticonSendRequest(
    roomId: Long,
    item: LiveEmoticonItem,
    replyMid: Long = 0,
    replayDmid: String = ""
): Result<LiveDanmakuSendRequest> {
    if (item.dmType == 0) {
        if (item.emoji.isBlank()) {
            return Result.failure(IllegalArgumentException("表情文字为空，无法发送"))
        }
        return Result.success(
            LiveDanmakuSendRequest(
                roomId = roomId,
                message = item.emoji,
                replyMid = replyMid,
                replayDmid = replayDmid
            )
        )
    }
    if (item.emoticonUnique.isBlank()) {
        return Result.failure(IllegalArgumentException("该独立表情缺少发送标识，无法发送"))
    }
    return Result.success(
        LiveDanmakuSendRequest(
            roomId = roomId,
            message = item.emoticonUnique,
            dmType = 1,
            emoticonOptions = "[object Object]"
        )
    )
}

fun appendLiveTextEmoticon(
    draft: String,
    item: LiveEmoticonItem,
    maxLength: Int
): Result<String> {
    if (item.dmType != 0 || item.emoji.isBlank()) {
        return Result.failure(IllegalArgumentException("该表情不能作为文字插入"))
    }
    val limit = maxLength.takeIf { it > 0 } ?: 40
    if (draft.length + item.emoji.length > limit) {
        return Result.failure(IllegalArgumentException("插入表情后超过弹幕字数限制"))
    }
    return Result.success(draft + item.emoji)
}

data class LiveVoteOption(val id: Int = 0, val description: String = "", val percent: Float = 0f)

data class LiveVoteInfo(
    val status: Int = 0,
    val question: String = "",
    val options: List<LiveVoteOption> = emptyList(),
    val durationMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val resultText: String = "",
    val endTimeText: String = "",
    val interactionId: Long = 0L
) {
    val isActive: Boolean get() = status == 4
}

data class LiveVoteSnapshot(
    val current: LiveVoteInfo? = null,
    val history: List<LiveVoteInfo> = emptyList()
)

val DefaultLiveReportReasons = listOf(
    LiveReportReason(id = 1, label = "违法违规", apiReason = "违法违规"),
    LiveReportReason(id = 2, label = "低俗色情", apiReason = "低俗色情"),
    LiveReportReason(id = 3, label = "垃圾广告", apiReason = "垃圾广告"),
    LiveReportReason(id = 4, label = "辱骂引战", apiReason = "辱骂引战"),
    LiveReportReason(id = 5, label = "政治敏感", apiReason = "政治敏感"),
    LiveReportReason(id = 6, label = "青少年不良信息", apiReason = "青少年不良信息"),
    LiveReportReason(id = 0, label = "其他", apiReason = "其他")
)
