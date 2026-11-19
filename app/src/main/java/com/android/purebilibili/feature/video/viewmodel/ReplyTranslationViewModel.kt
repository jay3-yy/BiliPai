package com.android.purebilibili.feature.video.viewmodel

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentGrpcRepository
import kotlinx.coroutines.launch

internal val LocalReplyTranslation = compositionLocalOf<ReplyTranslationViewModel?> { null }

internal data class ReplyTranslationState(val text: String, val visible: Boolean)

/** 会话级缓存翻译结果：开关只切换显示不重复请求，滚出屏幕或进出楼中楼后文本与展开态保留。 */
internal class ReplyTranslationViewModel : ViewModel() {
    private data class Key(val oid: Long, val type: Int, val rpid: Long)
    private val translations = mutableStateMapOf<Key, ReplyTranslationState>()
    private val pending = mutableStateListOf<Key>()

    fun state(item: ReplyItem): ReplyTranslationState? =
        translations[Key(item.oid, item.replyType, item.rpid)]

    fun isTranslating(item: ReplyItem): Boolean =
        Key(item.oid, item.replyType, item.rpid) in pending

    fun toggleVisible(item: ReplyItem) {
        val key = Key(item.oid, item.replyType, item.rpid)
        val state = translations[key] ?: return
        translations[key] = state.copy(visible = !state.visible)
    }

    fun translate(item: ReplyItem, onError: (String) -> Unit) {
        val key = Key(item.oid, item.replyType, item.rpid)
        if (key in pending || translations.containsKey(key)) return
        pending.add(key)
        viewModelScope.launch {
            try {
                CommentGrpcRepository.translateReply(
                    type = item.replyType.toLong(),
                    oid = item.oid,
                    rpid = item.rpid,
                ).fold(
                    onSuccess = { translated ->
                        if (translated.isNullOrBlank()) {
                            onError("翻译结果为空")
                        } else {
                            translations[key] = ReplyTranslationState(translated, visible = true)
                        }
                    },
                    onFailure = { e ->
                        onError("翻译失败: ${e.message ?: "请稍后重试"}")
                    },
                )
            } finally {
                pending.remove(key)
            }
        }
    }
}
