package com.android.purebilibili.feature.message.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.purebilibili.EXTRA_PENDING_NAVIGATION_ROUTE
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.app.MESSAGE_NOTIFICATION_CHANNEL_ID
import com.android.purebilibili.app.MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID
import com.android.purebilibili.app.resolveAppNotificationChannels
import com.android.purebilibili.navigation.ScreenRoutes

internal object MessageNotificationNotifier {
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        for (spec in resolveAppNotificationChannels()) {
            if (spec.id != MESSAGE_NOTIFICATION_CHANNEL_ID && spec.id != MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID) continue
            manager.createNotificationChannel(NotificationChannel(spec.id, spec.name, spec.importance).apply {
                description = spec.description
                setShowBadge(spec.showBadge)
                if (spec.silent) setSound(null, null)
            })
        }
    }

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(MESSAGE_NOTIFICATION_CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun contentIntent(context: Context, id: Int, key: String, route: String): PendingIntent =
        PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply {
                // Intent extras are not part of PendingIntent equality. A full identity avoids hash collisions.
                data = Uri.Builder().scheme("bilipai-notification").authority("message").appendPath(key).build()
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_PENDING_NAVIGATION_ROUTE, route)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun postAll(context: Context, mid: Long, result: MessageNotificationCheckResult, isCurrentSession: () -> Boolean) {
        val items = result.notifications
        if ((items.isEmpty() && result.cancelNotificationKeys.isEmpty()) || !canPost(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefix = "message:$mid:"
        // Cancel/notify are queued by Android. Build summaries from this snapshot plus the plan,
        // not from an immediate re-read that might still contain cancelled notifications.
        val active = manager.activeNotifications.filter { it.tag?.startsWith(prefix) == true }
        val affectedGroups = items.mapTo(linkedSetOf()) { it.group }
        for (key in result.cancelNotificationKeys) {
            affectedGroups += when {
                key.startsWith("dynamic:") -> "dynamic"
                key.startsWith("dm:") -> "dm"
                else -> "interactions"
            }
        }
        for (notification in active) {
            if (!isCurrentSession()) return
            if (notification.tag!!.removePrefix(prefix) in result.cancelNotificationKeys) {
                manager.cancel(notification.tag, notification.id)
            }
        }
        val replacedKeys = items.mapTo(hashSetOf()) { it.key }
        for (item in items) {
            if (!isCurrentSession()) return
            if (!item.isGroupSummary) post(context, mid, item)
        }
        for (group in affectedGroups) {
            if (!isCurrentSession()) return
            val survivors = active.filter {
                val key = it.tag!!.removePrefix(prefix)
                it.notification.group == "message:$mid:$group" &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    key !in result.cancelNotificationKeys && key !in replacedKeys
            }
            val incoming = items.filter { it.group == group && !it.isGroupSummary }
            if (survivors.isEmpty() && incoming.isEmpty() &&
                active.none { it.notification.group == "message:$mid:$group" }) continue
            val pendingCount = when (group) {
                "dynamic" -> result.state.pendingDynamicIds.size
                "dm" -> result.state.pendingPrivateSessionKeys.size
                else -> 0
            }
            val count = maxOf(pendingCount, survivors.size + incoming.size)
            val (id, route, title) = when (group) {
                "dm" -> Triple(5106, ScreenRoutes.Inbox.route, "新私信")
                "dynamic" -> Triple(5105, ScreenRoutes.Dynamic.route, "关注更新")
                "live" -> Triple(5107, ScreenRoutes.LiveFollowing.route, "开播提醒")
                else -> Triple(5108, ScreenRoutes.Inbox.route, "消息中心")
            }
            if (count == 0) {
                manager.cancel("${prefix}$group:summary", id)
                continue
            }
            val previews = buildList {
                for (item in incoming) add("${item.title}：${item.text}")
                for (notification in survivors) {
                    val extras = notification.notification.extras
                    add("${extras.getCharSequence(Notification.EXTRA_TITLE) ?: ""}：" +
                        (extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""))
                }
            }.joinToString("\n")
            val text = when (group) {
                "dynamic" -> "有 $count 条关注更新"
                "dm" -> "有 $count 个会话有未读消息"
                else -> previews
            }
            post(context, mid, PendingMessageNotification(
                "$group:summary", id, route, title,
                if ((group == "dynamic" || group == "dm") && previews.isNotBlank()) "$text\n$previews" else text,
                group, isGroupSummary = true,
            ))
        }
    }

    private fun post(context: Context, mid: Long, item: PendingMessageNotification) {
        if (!canPost(context)) return
        val key = "message:$mid:${item.key}"
        val notification = NotificationCompat.Builder(context, MESSAGE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(item.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("message:$mid:${item.group}")
            .setGroupSummary(item.isGroupSummary)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(item.isGroupSummary)
            .apply { if (item.isGroupSummary) setSilent(true) }
            .setContentIntent(contentIntent(context, item.notificationId, key, item.route))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key, item.notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the eligibility check and notify().
        }
    }

    fun activeMessageKeys(context: Context, mid: Long): Set<String> {
        val prefix = "message:$mid:"
        return context.getSystemService(NotificationManager::class.java).activeNotifications
            .mapNotNullTo(linkedSetOf()) { it.tag?.takeIf { tag -> tag.startsWith(prefix) }?.removePrefix(prefix) }
    }

    fun cancelOtherAccounts(context: Context, currentMid: Long?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val currentPrefix = currentMid?.let { "message:$it:" }
        manager.activeNotifications.filter {
            val tag = it.tag
            tag != null && tag.startsWith("message:") && (currentPrefix == null || !tag.startsWith(currentPrefix))
        }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun cancelMessages(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith("message:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}
