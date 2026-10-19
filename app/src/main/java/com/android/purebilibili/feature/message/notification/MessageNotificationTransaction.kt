package com.android.purebilibili.feature.message.notification

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** File locking also serializes the main-process Worker with the :message resident service. */
internal object MessageNotificationTransaction {
    private val mutex = Mutex()

    suspend fun <T> withLock(context: Context, block: suspend () -> T): T = withFileLock(
        File(context.applicationContext.filesDir, "message_notification_check.lock"), block
    )

    internal suspend fun <T> withFileLock(file: File, block: suspend () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "rw").use { owner ->
                var lock: java.nio.channels.FileLock? = null
                try {
                    while (lock == null) {
                        coroutineContext.ensureActive()
                        lock = try {
                            owner.channel.tryLock()
                        } catch (_: OverlappingFileLockException) {
                            null
                        }
                        if (lock == null) delay(100L)
                    }
                    block()
                } finally {
                    lock?.release()
                }
            }
        }
    }
}
