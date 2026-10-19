package com.android.purebilibili.feature.message.notification

import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNotificationTransactionTest {
    @Test
    fun concurrentScansNeverEnterTheTransactionTogether() = runTest {
        val directory = Files.createTempDirectory("message-lock").toFile()
        val lockFile = directory.resolve("check.lock")
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        try {
            List(20) {
                async(Dispatchers.IO) {
                    MessageNotificationTransaction.withFileLock(lockFile) {
                        val count = active.incrementAndGet()
                        maximum.updateAndGet { maxOf(it, count) }
                        yield()
                        active.decrementAndGet()
                    }
                }
            }.awaitAll()
            assertEquals(1, maximum.get())
            assertEquals(0, active.get())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failureReleasesTheFileLockForTheNextScan() = runTest {
        val directory = Files.createTempDirectory("message-lock").toFile()
        val lockFile = directory.resolve("check.lock")
        try {
            try {
                MessageNotificationTransaction.withFileLock(lockFile) { error("network failed") }
            } catch (_: IllegalStateException) {
                // The next scan must acquire both locks after an error.
            }
            assertTrue(MessageNotificationTransaction.withFileLock(lockFile) { true })
            RandomAccessFile(lockFile, "rw").use { owner ->
                owner.channel.tryLock().use { lock -> assertTrue(lock != null) }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun canceledWaitDoesNotEnterOrReleaseAnotherOwnersLock() = runTest {
        val directory = Files.createTempDirectory("message-lock").toFile()
        val lockFile = directory.resolve("check.lock")
        try {
            RandomAccessFile(lockFile, "rw").use { owner ->
                owner.channel.lock().use { held ->
                    val started = CompletableDeferred<Unit>()
                    var entered = false
                    val waiter = launch(Dispatchers.IO) {
                        started.complete(Unit)
                        MessageNotificationTransaction.withFileLock(lockFile) { entered = true }
                    }
                    started.await()
                    waiter.cancelAndJoin()
                    assertFalse(entered)
                    assertTrue(held.isValid)
                }
            }
            assertTrue(MessageNotificationTransaction.withFileLock(lockFile) { true })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun recentlyCompletedScanIsSharedOnlyForTheSameAccountAndBoot() {
        assertTrue(shouldSkipRecentNotificationCheck(1, 1, 29_999, 0, 5, 5))
        assertFalse(shouldSkipRecentNotificationCheck(1, 1, 30_000, 0, 5, 5))
        assertFalse(shouldSkipRecentNotificationCheck(2, 1, 100, 0, 5, 5))
        assertFalse(shouldSkipRecentNotificationCheck(1, 1, 100, 0, 6, 5))
        assertFalse(shouldSkipRecentNotificationCheck(1, 1, 100, 200, 5, 5))
        assertFalse(shouldSkipRecentNotificationCheck(1, null, 100, null, -1, null))
    }
}
