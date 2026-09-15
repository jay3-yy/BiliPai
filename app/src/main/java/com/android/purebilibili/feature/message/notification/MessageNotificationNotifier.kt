package com.android.purebilibili.feature.message.notification

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

    fun postAll(context: Context, mid: Long, items: List<PendingMessageNotification>, isCurrentSession: () -> Boolean) {
        if (items.isEmpty() || !canPost(context)) return
        ensureChannels(context)
        for (item in items) {
            if (!isCurrentSession()) return
            post(context, mid, item)
        }
        // Explicit Android groups require a stable summary even below the per-run overflow cap.
        for ((group, children) in items.groupBy { it.group }) {
            if (!isCurrentSession()) return
            if (children.any { it.isGroupSummary }) continue
            val (id, route, title) = when (group) {
                "dm" -> Triple(5106, ScreenRoutes.Inbox.route, "新私信")
                "dynamic" -> Triple(5105, ScreenRoutes.Dynamic.route, "关注更新")
                "live" -> Triple(5107, ScreenRoutes.LiveFollowing.route, "开播提醒")
                else -> Triple(5108, ScreenRoutes.Inbox.route, "消息中心")
            }
            post(context, mid, PendingMessageNotification(
                "$group:summary", id, route, title,
                children.joinToString("\n") { "${it.title}：${it.text}" }, group,
                isGroupSummary = true,
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
            .apply { if (item.isGroupSummary) setSilent(true) }
            .setContentIntent(contentIntent(context, item.notificationId, key, item.route))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key, item.notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the eligibility check and notify().
        }
    }

    fun cancelMessages(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith("message:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}
