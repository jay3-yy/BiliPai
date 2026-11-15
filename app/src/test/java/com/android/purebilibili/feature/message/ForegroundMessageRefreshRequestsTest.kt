package com.android.purebilibili.feature.message

import com.android.purebilibili.core.store.AccountSessionIdentity
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForegroundMessageRefreshRequestsTest {
    @Test
    fun pageReturnReusesInFlightQueryButReadEventsQueueAFollowUp() {
        val requests = ForegroundMessageRefreshRequests()
        val identity = AccountSessionIdentity(42L, 1L)
        val owner = Any()
        val signals = Channel<Unit>(Channel.CONFLATED)
        requests.bind(owner, identity) { signals.trySend(Unit) }
        repeat(3) { assertTrue(requests.request(identity, afterCurrentFetch = false)) }
        assertTrue(signals.tryReceive().isFailure)

        repeat(3) { assertTrue(requests.request(identity, afterCurrentFetch = true)) }
        assertTrue(signals.tryReceive().isSuccess)
        assertTrue(signals.tryReceive().isFailure)

        requests.setFetchInFlight(owner, false)
        assertTrue(requests.request(identity, afterCurrentFetch = false))
        assertTrue(signals.tryReceive().isSuccess)
        requests.setFetchInFlight(owner, true)
        requests.request(identity, afterCurrentFetch = false)
        assertTrue(signals.tryReceive().isFailure)
        signals.close()
    }

    @Test
    fun stoppedOrDifferentAccountSessionsCannotReceiveRefreshRequests() {
        val requests = ForegroundMessageRefreshRequests()
        val oldIdentity = AccountSessionIdentity(42L, 1L)
        val nextIdentity = AccountSessionIdentity(42L, 2L)
        val oldOwner = Any()
        val nextOwner = Any()
        var oldRefreshes = 0
        var nextRefreshes = 0
        assertFalse(requests.request(oldIdentity, true))
        requests.bind(oldOwner, oldIdentity) { oldRefreshes++ }
        requests.bind(nextOwner, nextIdentity) { nextRefreshes++ }
        requests.unbind(oldOwner)
        requests.setFetchInFlight(oldOwner, false)
        assertFalse(requests.request(oldIdentity, true))
        assertTrue(requests.request(nextIdentity, true))
        assertEquals(0, oldRefreshes)
        assertEquals(1, nextRefreshes)
        requests.unbind(nextOwner)
        assertFalse(requests.request(nextIdentity, true))
    }
}
