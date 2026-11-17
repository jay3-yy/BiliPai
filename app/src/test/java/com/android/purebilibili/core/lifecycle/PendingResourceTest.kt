package com.android.purebilibili.core.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PendingResourceTest {
    @Test
    fun `cancelled navigation releases unclaimed player once`() {
        val player = Any()
        val released = mutableListOf<Any>()
        val pending = PendingResource(player) { released += it }
        pending.dispose()
        pending.dispose()
        assertEquals(listOf(player), released)
        assertNull(pending.take())
    }

    @Test
    fun `page claim transfers release ownership and cannot be claimed again`() {
        val player = Any()
        val released = mutableListOf<Any>()
        val pending = PendingResource(player) { released += it }
        assertSame(player, pending.take())
        assertNull(pending.take())
        pending.dispose()
        assertEquals(emptyList(), released)
    }
}
