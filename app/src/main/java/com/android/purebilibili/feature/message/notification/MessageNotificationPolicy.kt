package com.android.purebilibili.feature.message.notification

import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.FollowedLiveRoom
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.feature.dynamic.components.DynamicCardPrimaryAction
import com.android.purebilibili.feature.dynamic.components.resolveDynamicCardPrimaryAction
import com.android.purebilibili.navigation.ScreenRoutes

internal data class MessageNotificationTiming(
    val periodicMinutes: Long,
    val residentPollSeconds: Long,
    val requiresBatteryNotLow: Boolean,
)

internal fun resolveMessageNotificationTiming(mode: MessageNotificationMode): MessageNotificationTiming = when (mode) {
    MessageNotificationMode.POWER_SAVING -> MessageNotificationTiming(30, 120, true)
    MessageNotificationMode.MORE_TIMELY -> MessageNotificationTiming(15, 60, false)
}

internal fun sessionNotificationKey(session: SessionItem): String = "${session.talker_id}_${session.session_type}"

internal fun filterNewPrivateSessions(
    sessions: List<SessionItem>,
    selfMid: Long,
    knownMsgKeys: Map<String, Long>,
): List<SessionItem> = sessions.filter { session ->
    val message = session.last_msg
    session.talker_id > 0 && session.unread_count > 0 && session.is_dnd == 0 && session.is_intercept == 0 &&
        message != null && message.msg_key > 0 && message.sender_uid != selfMid &&
        knownMsgKeys[sessionNotificationKey(session)] != message.msg_key
}.distinctBy(::sessionNotificationKey)

internal fun diffNewIds(ids: List<Long>, seen: List<Long>, isInitialized: Boolean): List<Long> {
    if (!isInitialized) return emptyList()
    val known = seen.toHashSet()
    return ids.filter { it > 0 && known.add(it) }
}

internal fun <T> evictBounded(items: List<T>, cap: Int): List<T> {
    require(cap > 0)
    val recent = LinkedHashSet<T>()
    for (item in items) {
        recent.remove(item)
        recent.add(item)
        if (recent.size > cap) recent.iterator().apply { next(); remove() }
    }
    return recent.toList()
}

// API pages are newest-first; persisted lists are oldest-first, including on initialization.
internal fun <T> mergeNotificationSeen(seen: List<T>, newestFirst: List<T>, cap: Int): List<T> =
    evictBounded(seen + newestFirst.asReversed(), cap)

internal fun shouldNotifyDynamicItem(item: DynamicItem, selfMid: Long): Boolean {
    val author = item.modules.module_author ?: return false
    if (!item.visible || item.id_str.isBlank() || author.mid <= 0 || author.mid == selfMid || author.following == false ||
        item.type == "DYNAMIC_TYPE_AD" || item.type == "DYNAMIC_TYPE_BANNER"
    ) return false
    return when (resolveDynamicCardPrimaryAction(item)) {
        is DynamicCardPrimaryAction.OpenUser, DynamicCardPrimaryAction.None -> false
        else -> true
    }
}

internal fun resolveLiveSessionTransitions(
    living: List<FollowedLiveRoom>,
    stored: Map<Long, Long>,
    isInitialized: Boolean,
): Pair<List<FollowedLiveRoom>, Map<Long, Long>> {
    val current = linkedMapOf<Long, Long>()
    val notifications = mutableListOf<FollowedLiveRoom>()
    for (room in living) {
        if (room.liveStatus != 1 || room.uid <= 0 || room.roomid <= 0 || current.containsKey(room.uid)) continue
        current[room.uid] = room.liveTime
        if (isInitialized && stored[room.uid] != room.liveTime) notifications.add(room)
    }
    return notifications to current
}

internal fun resolveDynamicNotificationRoute(item: DynamicItem): String? =
    when (val action = resolveDynamicCardPrimaryAction(item)) {
        is DynamicCardPrimaryAction.OpenVideo -> ScreenRoutes.VideoPlayer.createRoute(action.bvid)
        is DynamicCardPrimaryAction.OpenLive -> ScreenRoutes.Live.createRoute(action.roomId, action.title, action.uname)
        is DynamicCardPrimaryAction.OpenBangumi -> ScreenRoutes.BangumiDetail.createRoute(action.seasonId, action.epId)
        is DynamicCardPrimaryAction.OpenArticle -> ScreenRoutes.ArticleDetail.createRoute(action.articleId, action.title)
        is DynamicCardPrimaryAction.OpenDynamicDetail -> ScreenRoutes.DynamicDetail.createRoute(action.dynamicId)
        is DynamicCardPrimaryAction.OpenUser, DynamicCardPrimaryAction.None -> null
    }

internal data class PendingMessageNotification(
    val key: String,
    val notificationId: Int,
    val route: String,
    val title: String,
    val text: String,
    val group: String,
    val isGroupSummary: Boolean = false,
)
