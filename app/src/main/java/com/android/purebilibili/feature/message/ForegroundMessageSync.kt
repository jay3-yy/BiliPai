package com.android.purebilibili.feature.message

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.repository.MessageCenterUnread
import com.android.purebilibili.data.repository.MessageRepository
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.feature.message.notification.filterNewPrivateSessions
import com.android.purebilibili.feature.message.notification.sessionNotificationKey
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.catch
import com.android.purebilibili.core.network.grpc.BiliMessageBroadcast
import com.android.purebilibili.core.util.Logger
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ForegroundMessageSnapshot(
    val identity: AccountSessionIdentity,
    val unread: MessageCenterUnread,
    val sessionMessageKeys: Map<String, Long>?,
)

internal object ForegroundMessageSync {
    private val mutableSnapshot = MutableStateFlow<ForegroundMessageSnapshot?>(null)
    val snapshot = mutableSnapshot.asStateFlow()

    fun publish(identity: AccountSessionIdentity, unread: MessageCenterUnread, sessionMessageKeys: Map<String, Long>?) {
        if (TokenManager.accountIdentity.value == identity) {
            mutableSnapshot.value = ForegroundMessageSnapshot(identity, unread, sessionMessageKeys)
        }
    }
}

internal fun hasNewForegroundInteractions(previous: MessageCenterUnread, current: MessageCenterUnread): Boolean =
    current.reply > previous.reply ||
        current.at > previous.at || current.like > previous.like || current.sysMsg > previous.sysMsg

/** 每次前台会话单独建立基线；暂时移出列表的会话仍保留已见消息 ID。 */
internal class ForegroundPrivateMessageTracker {
    private var initialized = false
    private val knownMessageKeys = mutableMapOf<String, Long>()

    fun observe(sessions: List<SessionItem>, selfMid: Long): List<SessionItem> {
        val fresh = if (initialized) filterNewPrivateSessions(sessions, selfMid, knownMessageKeys) else emptyList()
        for (session in sessions) {
            val key = session.last_msg?.msg_key ?: continue
            if (key > 0L) knownMessageKeys[sessionNotificationKey(session)] = key
        }
        initialized = true
        return fresh
    }
}

/** 前台同步独立于后台通知设置；首次查询只建立基线，不提示历史未读。 */
@Composable
internal fun ForegroundMessageSyncHost(
    messagePageOpen: Boolean,
    enabled: Boolean = true,
) {
    val identity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    val context = LocalContext.current
    val latestMessagePageOpen by rememberUpdatedState(messagePageOpen)
    LaunchedEffect(identity, lifecycle, focused, enabled) {
        if (!enabled || !focused || identity.mid == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val refreshSignals = Channel<Unit>(Channel.CONFLATED)
            launch {
                BiliMessageBroadcast.notifications(identity)
                    .retryWhen { error, _ ->
                        if (error !is IOException || TokenManager.accountIdentity.value != identity) {
                            false
                        } else {
                            Logger.w("MessageBroadcast", "连接中断，15 秒后重新订阅：${error.javaClass.simpleName}")
                            delay(15_000L)
                            true
                        }
                    }
                    .catch { error -> Logger.e("MessageBroadcast", "消息订阅失败，前台定期同步继续运行", error) }
                    .collect { refreshSignals.trySend(Unit) }
            }
            var previous: MessageCenterUnread? = null
            var sessionMessageKeys: Map<String, Long>? = null
            val privateTracker = ForegroundPrivateMessageTracker()
            while (true) {
                val (unreadResult, sessionsResult) = coroutineScope {
                    val unread = async { MessageRepository.getMessageCenterUnread() }
                    val sessions = async { MessageRepository.getSessions() }
                    unread.await() to sessions.await()
                }
                if (TokenManager.accountIdentity.value != identity) return@repeatOnLifecycle
                var newMessage = false
                unreadResult.onSuccess { unread ->
                    val before = previous
                    newMessage = before != null && hasNewForegroundInteractions(before, unread)
                    previous = unread
                }
                sessionsResult.onSuccess { page ->
                    val sessions = page.session_list.orEmpty()
                    val fresh = privateTracker.observe(sessions, checkNotNull(identity.mid))
                    newMessage = newMessage || fresh.isNotEmpty()
                    sessionMessageKeys = sessions.mapNotNull { session ->
                        session.last_msg?.msg_key?.takeIf { it > 0L }?.let {
                            sessionNotificationKey(session) to it
                        }
                    }.toMap()
                }
                if (newMessage) {
                    com.android.purebilibili.core.store.MessageBadgeCleanState.onNewMessage(checkNotNull(identity.mid))
                }
                previous?.let { ForegroundMessageSync.publish(identity, it, sessionMessageKeys) }
                if (newMessage && !latestMessagePageOpen) {
                    Toast.makeText(context, "收到新消息，可前往「我的 → 消息中心」查看", Toast.LENGTH_LONG).show()
                }
                withTimeoutOrNull(15_000L) { refreshSignals.receive() }
            }
        }
    }
}
