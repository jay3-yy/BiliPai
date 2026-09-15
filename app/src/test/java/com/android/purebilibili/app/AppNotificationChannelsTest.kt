package com.android.purebilibili.app

import android.app.NotificationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNotificationChannelsTest {
    @Test
    fun messageChannelAllowsAlertsAndBadges() {
        val channel = resolveAppNotificationChannels().single { it.id == MESSAGE_NOTIFICATION_CHANNEL_ID }
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertTrue(channel.showBadge)
        assertFalse(channel.silent)
    }

    @Test
    fun residentServiceDoesNotSoundOrCountAsUnreadMessage() {
        val channel = resolveAppNotificationChannels().single { it.id == MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID }
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.showBadge)
        assertTrue(channel.silent)
    }
}
