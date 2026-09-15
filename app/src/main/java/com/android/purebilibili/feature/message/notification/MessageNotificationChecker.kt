package com.android.purebilibili.feature.message.notification

import android.content.Context
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.DynamicFeedData
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.FollowedLiveData
import com.android.purebilibili.data.model.response.FollowedLiveRoom
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.data.model.response.SystemNoticeItem
import com.android.purebilibili.data.repository.MessageRepository
import com.android.purebilibili.feature.message.MessagePreviewParser
import com.android.purebilibili.navigation.ScreenRoutes
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

// The boundary returns read-only snapshots, never server acknowledgements or UI paging state.
internal interface MessageNotificationSource {
    suspend fun sessions(): List<SessionItem>
    suspend fun replyIds(): List<Long>
    suspend fun atIds(): List<Long>
    suspend fun likeIds(): List<Long>
    suspend fun systemNotices(): List<SystemNoticeItem>
    suspend fun dynamicUpdateCount(baseline: String): Int
    suspend fun dynamicFeed(baseline: String, offset: String): DynamicFeedData
    suspend fun followedLive(page: Int): FollowedLiveData
}

private object NetworkMessageNotificationSource : MessageNotificationSource {
    override suspend fun sessions() = MessageRepository.getSessions(sessionType = 4, size = 20)
        .getOrThrow().session_list.orEmpty()
    override suspend fun replyIds() = MessageRepository.getReplyFeed().getOrThrow().items.orEmpty().map { it.id }
    override suspend fun atIds() = MessageRepository.getAtFeed().getOrThrow().items.orEmpty().map { it.id }
    override suspend fun likeIds(): List<Long> {
        val data = MessageRepository.getLikeFeed().getOrThrow()
        return (data.latest?.items.orEmpty() + data.total?.items.orEmpty()).map { it.id }.distinct()
    }
    override suspend fun systemNotices() = MessageRepository.getSystemNotices().getOrThrow()
    override suspend fun dynamicUpdateCount(baseline: String): Int {
        val response = NetworkModule.dynamicApi.getDynamicUpdateCount(type = "all", updateBaseline = baseline)
        check(response.code == 0) { "Dynamic update code ${response.code}" }
        return checkNotNull(response.data).update_num
    }
    override suspend fun dynamicFeed(baseline: String, offset: String): DynamicFeedData {
        val response = NetworkModule.dynamicApi.getDynamicFeed(type = "all", offset = offset, updateBaseline = baseline)
        check(response.code == 0) { "Dynamic feed code ${response.code}" }
        return checkNotNull(response.data)
    }
    override suspend fun followedLive(page: Int): FollowedLiveData {
        val response = NetworkModule.api.getFollowedLive(page = page, pageSize = 10)
        check(response.code == 0) { "Followed live code ${response.code}" }
        // A missing payload is not evidence that every previously live room went offline.
        return checkNotNull(response.data)
    }
}

internal data class MessageNotificationCheckResult(
    val state: AccountNotificationState,
    val notifications: List<PendingMessageNotification>,
    val shouldRetry: Boolean,
)

private class NotificationSessionChanged : Exception()

internal class MessageNotificationPoller(
    private val source: MessageNotificationSource,
    private val ensureSession: () -> Unit = {},
) {
    private suspend fun <T> fetch(block: suspend () -> T): T {
        ensureSession()
        val result = block()
        ensureSession()
        return result
    }

    suspend fun check(
        settings: MessageNotificationSettings,
        previous: AccountNotificationState,
        selfMid: Long,
    ): MessageNotificationCheckResult {
        if (!settings.enabled) return MessageNotificationCheckResult(previous, emptyList(), false)
        var state = previous
        var shouldRetry = false
        val notifications = mutableListOf<PendingMessageNotification>()

        suspend fun category(id: String, block: suspend () -> Unit) {
            val before = state
            val notificationCount = notifications.size
            try {
                block()
                state = state.copy(initialized = state.initialized + id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotificationSessionChanged) {
                throw e
            } catch (e: Exception) {
                state = before
                notifications.subList(notificationCount, notifications.size).clear()
                if (e is IOException) shouldRetry = true
            }
        }

        fun feed(id: String, ids: List<Long>, seen: List<Long>, notificationId: Int, route: String, title: String, text: (Int) -> String): List<Long> {
            val newIds = diffNewIds(ids, seen, id in state.initialized)
            if (newIds.isNotEmpty()) {
                notifications += PendingMessageNotification(id, notificationId, route, title, text(newIds.size), "interactions")
            }
            return mergeNotificationSeen(seen, ids.filter { it > 0 }, 100)
        }

        if (settings.notifyMessageCenter) {
            category("dm") {
                val sessions = fetch { source.sessions() }
                val fresh = if ("dm" in state.initialized) filterNewPrivateSessions(sessions, selfMid, state.sessionMsgKeys) else emptyList()
                for (session in fresh.take(5)) {
                    val message = checkNotNull(session.last_msg)
                    val name = session.account_info?.name?.takeIf { it.isNotBlank() } ?: "私信"
                    val prefix = if (session.unread_count > 1) "[${session.unread_count} 条新消息] " else ""
                    val key = sessionNotificationKey(session)
                    notifications += PendingMessageNotification(
                        "dm:$key", 6000 + ("${session.talker_id}:${session.session_type}".hashCode() and 0xFFFF),
                        ScreenRoutes.Chat.createRoute(session.talker_id, session.session_type, name), name,
                        prefix + MessagePreviewParser.parseSessionPreview(message.content, message.msg_type), "dm",
                    )
                }
                if (fresh.size > 5) notifications += PendingMessageNotification(
                    "dm:summary", 5106, ScreenRoutes.Inbox.route, "新私信", "有 ${fresh.size} 个会话发来新消息", "dm",
                    isGroupSummary = true,
                )
                val keys = LinkedHashMap(state.sessionMsgKeys)
                for (session in sessions.asReversed()) {
                    val message = session.last_msg ?: continue
                    if (session.talker_id <= 0 || message.msg_key <= 0) continue
                    val key = sessionNotificationKey(session)
                    keys.remove(key)
                    keys[key] = message.msg_key
                    if (keys.size > 100) keys.entries.iterator().apply { next(); remove() }
                }
                state = state.copy(sessionMsgKeys = keys)
            }
            category("reply") {
                state = state.copy(seenReplyIds = feed("reply", fetch { source.replyIds() }, state.seenReplyIds,
                    5101, ScreenRoutes.ReplyMe.route, "回复我的") { "你有 $it 条新回复" })
            }
            category("at") {
                state = state.copy(seenAtIds = feed("at", fetch { source.atIds() }, state.seenAtIds,
                    5102, ScreenRoutes.AtMe.route, "@我") { "有 $it 条新消息提到你" })
            }
            category("like") {
                state = state.copy(seenLikeIds = feed("like", fetch { source.likeIds() }, state.seenLikeIds,
                    5103, ScreenRoutes.LikeMe.route, "收到的赞") { "你的内容收到 $it 个新赞" })
            }
            category("sysmsg") {
                val items = fetch { source.systemNotices() }
                val known = state.seenSystemCursors.toHashSet()
                val latest = items.firstOrNull { it.cursor !in known }
                if ("sysmsg" in state.initialized && latest != null) notifications += PendingMessageNotification(
                    "sysmsg", 5104, ScreenRoutes.SystemNotice.route, "系统通知", latest.title.ifBlank { latest.content }, "interactions",
                )
                state = state.copy(seenSystemCursors = mergeNotificationSeen(state.seenSystemCursors, items.map { it.cursor }, 100))
            }
        }
        if (settings.notifyDynamicUpdates) category("dynamic") {
            val baseline = state.dynamicBaseline
            val initializing = "dynamic" !in state.initialized || baseline.isBlank()
            if (initializing || fetch { source.dynamicUpdateCount(baseline) } > 0) {
                val firstPage = fetch { source.dynamicFeed(baseline, "") }
                val items = ArrayList<DynamicItem>(firstPage.items)
                if (!initializing) {
                    var page = firstPage
                    val offsets = hashSetOf("")
                    var pageCount = 1
                    while (pageCount < 3 && page.has_more && page.offset.isNotBlank() && offsets.add(page.offset)) {
                        page = fetch { source.dynamicFeed(baseline, page.offset) }
                        items.addAll(page.items)
                        pageCount++
                    }
                }
                val ids = items.map { it.id_str }.filter { it.isNotBlank() }
                if (!initializing) {
                    val known = state.seenDynamicIds.toHashSet()
                    val fresh = items.filter { known.add(it.id_str) && shouldNotifyDynamicItem(it, selfMid) }
                    for (item in fresh.take(3)) {
                        val route = resolveDynamicNotificationRoute(item) ?: continue
                        val author = item.modules.module_author?.name.orEmpty().ifBlank { "关注的 UP 主" }
                        val archive = item.modules.module_dynamic?.major?.archive
                        notifications += PendingMessageNotification(
                            "dynamic:${item.id_str}", 8000 + (item.id_str.hashCode() and 0xFFFF), route,
                            "$author 发布了${if (archive != null) "新视频" else "新动态"}", archive?.title.orEmpty(), "dynamic",
                        )
                    }
                    if (fresh.size > 3) notifications += PendingMessageNotification(
                        "dynamic:summary", 5105, ScreenRoutes.Dynamic.route, "关注更新", "有 ${fresh.size} 条关注更新", "dynamic",
                        isGroupSummary = true,
                    )
                }
                state = state.copy(
                    dynamicBaseline = firstPage.update_baseline.ifBlank {
                        if (initializing) ids.firstOrNull().orEmpty() else baseline
                    },
                    seenDynamicIds = mergeNotificationSeen(state.seenDynamicIds, ids, 200),
                )
            }
        }
        if (settings.notifyLiveAlerts) category("live") {
            val living = linkedMapOf<Long, FollowedLiveRoom>()
            var complete = false
            for (page in 1..10) {
                val data = fetch { source.followedLive(page) }
                val rooms = data.list.orEmpty()
                for (room in rooms) if (room.liveStatus == 1) living.putIfAbsent(room.roomid, room)
                if (rooms.isEmpty() || data.livingNum == 0 || living.size >= data.livingNum ||
                    (data.pageinfo?.total_page?.let { it > 0 && page >= it } == true)) {
                    complete = true
                    break
                }
            }
            val (fresh, current) = resolveLiveSessionTransitions(living.values.toList(), state.liveSessions, "live" in state.initialized)
            for (room in fresh.take(5)) notifications += PendingMessageNotification(
                "live:${room.uid}", 7000 + (room.uid % 100000).toInt(),
                ScreenRoutes.Live.createRoute(room.roomid, room.title, room.uname), "开播提醒",
                "${room.uname} 正在直播" + if (room.title.isBlank()) "" else "：${room.title}", "live",
            )
            // Hitting the page bound is not an offline transition for rooms outside the scan.
            state = state.copy(liveSessions = if (complete) current else state.liveSessions + current)
        }
        return MessageNotificationCheckResult(state, notifications, shouldRetry)
    }
}

internal class MessageNotificationChecker(private val context: Context) {
    suspend fun runCheck(): Boolean = messageNotificationMutex.withLock {
        val settings = MessageNotificationSettingsStore.getSettings(context).first()
        if (!settings.enabled || !MessageNotificationNotifier.canPost(context)) return@withLock true
        val mid = TokenManager.midCache?.takeIf { it > 0 } ?: return@withLock true
        val session = TokenManager.sessDataCache?.takeIf { it.isNotBlank() } ?: return@withLock true
        fun isSameSession() = TokenManager.midCache == mid && TokenManager.sessDataCache == session
        val result = try {
            MessageNotificationPoller(NetworkMessageNotificationSource) {
                if (!isSameSession()) throw NotificationSessionChanged()
            }.check(settings, MessageNotificationStateStore.getState(context, mid), mid)
        } catch (_: NotificationSessionChanged) {
            return@withLock true
        }
        if (!isSameSession() || MessageNotificationSettingsStore.getSettings(context).first() != settings ||
            !MessageNotificationNotifier.canPost(context)) return@withLock true
        MessageNotificationStateStore.putState(context, mid, result.state)
        MessageNotificationNotifier.postAll(context, mid, result.notifications, ::isSameSession)
        !result.shouldRetry
    }
}
