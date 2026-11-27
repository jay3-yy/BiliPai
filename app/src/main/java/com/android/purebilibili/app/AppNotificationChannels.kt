package com.android.purebilibili.app

import android.app.NotificationManager

internal const val MEDIA_PLAYBACK_NOTIFICATION_CHANNEL_ID = "media_playback_channel"
internal const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
internal const val LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "live_update_channel"
internal const val SPONSOR_BLOCK_NOTIFICATION_CHANNEL_ID = "sponsor_block_channel"
internal const val JSON_PLUGIN_STATS_NOTIFICATION_CHANNEL_ID = "json_plugin_stats_channel"
internal const val MESSAGE_NOTIFICATION_CHANNEL_ID = "message_notification_channel"
internal const val MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID = "message_notification_service_channel"
internal const val LIVE_RESERVE_NOTIFICATION_CHANNEL_ID = "live_reserve_notification_channel"
internal const val CUSTOM_PUSH_VIDEO_CHANNEL_ID = "custom_push_video_channel"
internal const val CUSTOM_PUSH_LIVE_CHANNEL_ID = "custom_push_live_channel"
internal const val CUSTOM_PUSH_DYNAMIC_CHANNEL_ID = "custom_push_dynamic_channel"
internal const val CUSTOM_PUSH_MESSAGE_CHANNEL_ID = "custom_push_message_channel"
internal const val CUSTOM_PUSH_TEST_CHANNEL_ID = "custom_push_test_channel"

internal data class AppNotificationChannelSpec(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
    val showBadge: Boolean = false,
    val silent: Boolean = true
)

internal fun resolveAppNotificationChannels(): List<AppNotificationChannelSpec> {
    return listOf(
        AppNotificationChannelSpec(
            id = MEDIA_PLAYBACK_NOTIFICATION_CHANNEL_ID,
            name = "媒体播放",
            description = "显示正在播放的视频控制条",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        AppNotificationChannelSpec(
            id = DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            name = "下载任务",
            description = "显示后台下载进度",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        AppNotificationChannelSpec(
            id = LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            name = "实时活动状态",
            description = "在状态栏与锁屏实时胶囊中显示下载进度与睡眠定时",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            showBadge = false,
            silent = true
        ),
        AppNotificationChannelSpec(
            id = SPONSOR_BLOCK_NOTIFICATION_CHANNEL_ID,
            name = "空降助手",
            description = "显示空降助手每日节省时间汇总",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        AppNotificationChannelSpec(
            id = JSON_PLUGIN_STATS_NOTIFICATION_CHANNEL_ID,
            name = "插件统计",
            description = "显示 JSON 规则插件过滤数量汇总",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        AppNotificationChannelSpec(
            id = MESSAGE_NOTIFICATION_CHANNEL_ID,
            name = "消息通知",
            description = "私信、互动消息、关注更新与开播提醒",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            showBadge = true,
            silent = false
        ),
        AppNotificationChannelSpec(
            id = MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID,
            name = "后台消息监测",
            description = "常驻后台监测消息时显示的常驻通知",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        AppNotificationChannelSpec(
            id = LIVE_RESERVE_NOTIFICATION_CHANNEL_ID,
            name = "直播预约提醒",
            description = "提醒已预约的直播即将开播",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            showBadge = true,
            silent = false,
        ),
        AppNotificationChannelSpec(
            id = CUSTOM_PUSH_VIDEO_CHANNEL_ID,
            name = "自建推送 · UP主投稿",
            description = "来自自建推送服务器的新视频投稿提醒",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            showBadge = true,
            silent = false,
        ),
        AppNotificationChannelSpec(
            id = CUSTOM_PUSH_LIVE_CHANNEL_ID,
            name = "自建推送 · 直播开播",
            description = "来自自建推送服务器的直播开播提醒",
            importance = NotificationManager.IMPORTANCE_HIGH,
            showBadge = true,
            silent = false,
        ),
        AppNotificationChannelSpec(
            id = CUSTOM_PUSH_DYNAMIC_CHANNEL_ID,
            name = "自建推送 · 动态更新",
            description = "来自自建推送服务器的关注动态提醒",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            showBadge = true,
            silent = false,
        ),
        AppNotificationChannelSpec(
            id = CUSTOM_PUSH_MESSAGE_CHANNEL_ID,
            name = "自建推送 · 回复与私信",
            description = "来自自建推送服务器的互动回复与私信提醒",
            importance = NotificationManager.IMPORTANCE_HIGH,
            showBadge = true,
            silent = false,
        ),
        AppNotificationChannelSpec(
            id = CUSTOM_PUSH_TEST_CHANNEL_ID,
            name = "自建推送 · 测试通知",
            description = "用于验证自建推送链路与加解密的测试通知",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            showBadge = false,
            silent = false,
        ),
    )
}
