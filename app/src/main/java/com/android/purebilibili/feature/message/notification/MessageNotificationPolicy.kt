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

// Resident FGS keeps the fast lane only while the user is around (screen on) or the device is
// charging; an idle device drops to a 15-minute floor so the 60–120s poll stops burning radio/CPU
// overnight while WorkManager keeps its slower periodic guarantee.
internal const val RESIDENT_IDLE_POLL_MS: Long = 15 * 60 * 1000L

internal fun resolveResidentPollDelayMs(mode: MessageNotificationMode, screenOn: Boolean, charging: Boolean): Long {
    val base = resolveMessageNotificationTiming(mode).residentPollSeconds * 1000
    return if (screenOn || charging) base else RESIDENT_IDLE_POLL_MS
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

internal const val MAX_DYNAMIC_NOTIFICATION_PREVIEWS = 3
internal const val MAX_DYNAMIC_NOTIFICATION_CONCURRENCY = 16

// Dynamic IDs are decimal publish baselines. Compare without Long overflow or BigInteger allocation.
internal fun isNewerDynamicNotificationId(id: String, baseline: String): Boolean =
    id.isNotEmpty() && (id.length > baseline.length || (id.length == baseline.length && id > baseline))

internal fun addNewestDynamicNotificationPreview(items: MutableList<DynamicItem>, item: DynamicItem) {
    var index = 0
    while (index < items.size && !isNewerDynamicNotificationId(item.id_str, items[index].id_str)) index++
    if (index >= MAX_DYNAMIC_NOTIFICATION_PREVIEWS) return
    items.add(index, item)
    if (items.size > MAX_DYNAMIC_NOTIFICATION_PREVIEWS) items.removeAt(MAX_DYNAMIC_NOTIFICATION_PREVIEWS)
}

internal fun dynamicNotificationText(item: DynamicItem): String {
    val content = item.modules.module_dynamic
    val major = content?.major
    return major?.archive?.title?.takeIf { it.isNotBlank() }
        ?: major?.pgc?.title?.takeIf { it.isNotBlank() }
        ?: major?.article?.title?.takeIf { it.isNotBlank() }
        ?: major?.opus?.title?.takeIf { it.isNotBlank() }
        ?: content?.desc?.text?.takeIf { it.isNotBlank() }
        ?: major?.opus?.summary?.text?.takeIf { it.isNotBlank() }
        ?: major?.ugc_season?.title?.takeIf { it.isNotBlank() }
        ?: major?.common?.title?.takeIf { it.isNotBlank() }
        ?: major?.music?.title?.takeIf { it.isNotBlank() }
        ?: item.orig?.let(::dynamicNotificationText)
        ?: ""
}

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

// Suppress adjacent Worker/resident scans, without carrying elapsed timestamps across reboots.
internal fun shouldSkipRecentNotificationCheck(
    mid: Long,
    lastMid: Long?,
    nowElapsedMs: Long,
    lastElapsedMs: Long?,
    boot: Long,
    lastBoot: Long?
): Boolean = mid == lastMid && boot >= 0L && boot == lastBoot && lastElapsedMs != null &&
    nowElapsedMs >= lastElapsedMs && nowElapsedMs - lastElapsedMs < 30_000L
