package com.android.purebilibili.core.lifecycle

import android.app.Application
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class FairMemoryReceiverTest {
    private class ReplyBinder : Binder() {
        val replies = mutableListOf<Triple<Int, Int, Int>>()

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            assertEquals(IBinder.FIRST_CALL_TRANSACTION, code)
            assertEquals(IBinder.FLAG_ONEWAY, flags)
            assertNull(reply)
            replies += Triple(data.readInt(), data.readInt(), data.readInt())
            assertTrue(requireNotNull(data.readBundle(javaClass.classLoader)).isEmpty)
            return true
        }
    }

    private fun intent(type: Int = 1000, id: Int = 0, callback: IBinder = ReplyBinder()): Intent =
        Intent(FAIR_MEMORY_TRIM_ACTION).putExtra("common", Bundle().apply {
            putInt("notifyType", type)
            putInt("notifyId", id)
            putString("action", "trim")
            putBinder("callback", callback)
        })

    @Test
    fun physicalAndHeapWarningsReplyToTheirOwnNotification() {
        val physical = ReplyBinder()
        val heap = ReplyBinder()
        val first = requireNotNull(parseFairMemoryRequest(intent(1000, 12, physical)))
        val second = requireNotNull(parseFairMemoryRequest(intent(2000, -7, heap)))

        assertTrue(replyFairMemory(second, FAIR_MEMORY_NOT_HANDLED))
        assertTrue(replyFairMemory(first, FAIR_MEMORY_HANDLED))

        assertEquals(listOf(Triple(1000, 12, 0)), physical.replies)
        assertEquals(listOf(Triple(2000, -7, 1)), heap.replies)
    }

    @Test
    fun incompleteWarningsCannotBecomeSuccessfulDefaultNotifications() {
        val valid = intent()
        assertEquals(0, requireNotNull(parseFairMemoryRequest(valid)).notifyId)
        listOf("notifyType", "notifyId", "action", "callback").forEach { key ->
            val common = Bundle(requireNotNull(valid.getBundleExtra("common"))).apply { remove(key) }
            assertNull(parseFairMemoryRequest(Intent(FAIR_MEMORY_TRIM_ACTION).putExtra("common", common)))
        }
        assertNull(parseFairMemoryRequest(intent(type = 999)))
        assertNull(parseFairMemoryRequest(Intent(FAIR_MEMORY_TRIM_ACTION)))
    }

    @Test
    fun terminationMustMatchItsPayloadAndPreserveItsOwnOperation() {
        assertNull(parseFairMemoryRequest(intent().setAction(FAIR_MEMORY_KILL_ACTION)))
        val common = requireNotNull(intent().getBundleExtra("common")).apply { putString("action", "kill") }
        assertNull(parseFairMemoryRequest(Intent(FAIR_MEMORY_TRIM_ACTION).putExtra("common", common)))
        val kill = requireNotNull(parseFairMemoryRequest(Intent(FAIR_MEMORY_KILL_ACTION).putExtra("common", common)))
        assertEquals(FairMemoryOperation.KILL, kill.operation)
        assertEquals(FairMemoryOperation.TRIM, requireNotNull(parseFairMemoryRequest(intent())).operation)
    }

    @Test
    fun successIsReturnedAfterWorkActuallyCompletes() = runTest {
        var completed = false
        val result = executeFairMemoryRequest(2500L) {
            delay(100L)
            completed = true
        }
        assertTrue(completed)
        assertEquals(FAIR_MEMORY_HANDLED, result)
    }

    @Test
    fun expiredAndTimedOutWorkCannotReportSuccess() = runTest {
        var started = false
        assertEquals(FAIR_MEMORY_NOT_HANDLED, executeFairMemoryRequest(0L) { started = true })
        assertFalse(started)

        var cancelled = false
        assertEquals(FAIR_MEMORY_NOT_HANDLED, executeFairMemoryRequest(100L) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        })
        assertTrue(cancelled)
    }

    @Test
    fun errorsReportFailureAndParentCancellationPropagates() = runTest {
        assertEquals(FAIR_MEMORY_NOT_HANDLED, executeFairMemoryRequest(2500L) {
            error("cache trim failed")
        })
        val cancellation = CancellationException("service destroyed")
        assertSame(cancellation, assertFailsWith<CancellationException> {
            executeFairMemoryRequest(2500L) { throw cancellation }
        })
    }
}
