package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class LiveEmoticonRootResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: LiveEmoticonData? = null,
    val message: String = ""
) {
    val errorMessage: String get() = message.ifBlank { msg }
}

@Serializable
data class LiveEmoticonData(
    val data: List<EmoticonPkg>? = null
)

@Serializable
data class EmoticonPkg(
    val pkg_id: Int = 0,
    val pkg_name: String = "",
    val pkg_type: Int = 0,
    val emoticons: List<LiveDanmakuEmoticon>? = null
)

@Serializable
data class LiveDanmakuEmoticon(
    val emoji: String = "",
    val url: String = "",
    val des: String = "",
    // 独立大表情发送时用作 msg；普通文本表情不使用此 ID。
    val emoticon_unique: String = "",
    val width: Int? = null,
    val height: Int? = null
)
