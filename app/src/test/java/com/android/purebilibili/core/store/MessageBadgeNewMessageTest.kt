package com.android.purebilibili.core.store

import org.junit.Test
import kotlin.test.assertEquals

class MessageBadgeNewMessageTest {
    @Test
    fun confirmedMessageRestoresCleanedBadgeWithoutTotalIncrease() {
        MessageBadgeCleanState.clean(100L)
        assertEquals(0, MessageBadgeCleanState.resolve(100L, 3))
        MessageBadgeCleanState.onNewMessage(100L)
        assertEquals(3, MessageBadgeCleanState.resolve(100L, 3))
    }

    @Test
    fun anotherAccountsMessageCannotRestoreCurrentBadge() {
        MessageBadgeCleanState.clean(200L)
        assertEquals(0, MessageBadgeCleanState.resolve(200L, 3))
        MessageBadgeCleanState.onNewMessage(100L)
        assertEquals(0, MessageBadgeCleanState.resolve(200L, 3))
    }
}
