package com.android.purebilibili.feature.nearby

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.Closeable
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NearbyDuplexConnectionTest {
    @Test fun identityCodeIsSymmetricAndChangesWhenEitherIdentityChanges() {
        val first = "a".repeat(64)
        val second = "b".repeat(64)
        assertEquals(nearbyVerificationCode(first, second), nearbyVerificationCode(second, first))
        assertNotEquals(nearbyVerificationCode(first, second), nearbyVerificationCode(first, "c".repeat(64)))
        assertEquals(32, nearbyVerificationCode(first, second).replace(" ", "").length)
    }

    @Test fun receiverCanRequestFreshPositionWhileOriginalOfferIsWaiting() = runTest {
        withContext(Dispatchers.IO) {
            val aInput = PipedInputStream(32_768)
            val bOutput = PipedOutputStream(aInput)
            val bInput = PipedInputStream(32_768)
            val aOutput = PipedOutputStream(bInput)
            val aResource = Closeable { aInput.close(); aOutput.close() }
            val bResource = Closeable { bInput.close(); bOutput.close() }
            val aId = "a".repeat(32)
            val bId = "b".repeat(32)
            val aPeer = NearbyPeer(aId, "手机", "a".repeat(64), "192.168.1.2", NEARBY_PORT)
            val bPeer = NearbyPeer(bId, "模拟器", "b".repeat(64), "10.0.2.16", NEARBY_PORT)
            var position = 100L
            val sender = NearbyDuplexConnection(bPeer, aResource, aInput, aOutput, aId) { _, request ->
                assertEquals("snapshot", request.kind)
                NearbyMessage(kind = "snapshot", id = aId, transferId = request.transferId,
                    video = NearbyVideo("BV1xx411c7mD", 12, position, 1f, "视频", ""), accepted = true)
            }
            val receiver = NearbyDuplexConnection(aPeer, bResource, bInput, bOutput, bId) { link, offer ->
                position = 9_000L // 模拟接收端准备期间发送端继续播放。
                val latest = link.request(NearbyMessage(kind = "snapshot", id = bId, transferId = offer.transferId))
                assertEquals(9_000L, latest.video?.positionMs)
                NearbyMessage(kind = "playing", id = bId, transferId = offer.transferId, accepted = true)
            }
            val senderReader = launch(Dispatchers.IO) { try { sender.run() } catch (_: IOException) { /* 流关闭。 */ } }
            val receiverReader = launch(Dispatchers.IO) { try { receiver.run() } catch (_: IOException) { /* 流关闭。 */ } }
            try {
                withTimeout(5_000) {
                    val result = sender.request(NearbyMessage(kind = "video", id = aId, transferId = nearbyToken()))
                    assertEquals("playing", result.kind)
                    assertEquals(true, result.accepted)
                    // 反方向继续使用原流，无需连接模拟器地址。
                    val reverse = receiver.request(NearbyMessage(kind = "snapshot", id = bId, transferId = nearbyToken()))
                    assertEquals(9_000L, reverse.video?.positionMs)
                }
            } finally {
                sender.close(); receiver.close()
                senderReader.cancel(); receiverReader.cancel()
            }
        }
    }

    @Test fun closingFailsPendingAndFutureRequestsWithTheOriginalReason() = runTest {
        withContext(Dispatchers.IO) {
            val written = CompletableDeferred<Unit>()
            val output = object : ByteArrayOutputStream() {
                override fun flush() { written.complete(Unit) }
            }
            val peer = NearbyPeer("b".repeat(32), "设备", "b".repeat(64), "192.168.1.2", NEARBY_PORT)
            val link = NearbyDuplexConnection(peer, Closeable {}, ByteArrayInputStream(byteArrayOf()),
                output, "a".repeat(32)) { _, _ -> error("此用例不接收请求") }
            val request = NearbyMessage(kind = "hello", id = "a".repeat(32))
            val result = async {
                try { link.request(request); error("关闭后不得继续等待") }
                catch (error: IOException) { error }
            }
            try {
                withTimeout(5_000) {
                    written.await()
                    val reason = NearbyConnectionFailure("两端设备接力协议不兼容，请使用相同版本")
                    link.close(reason)
                    link.close()
                    assertSame(reason, result.await())
                    assertSame(reason, assertFailsWith<NearbyConnectionFailure> { link.request(request) })
                }
            } finally { link.close(); result.cancel() }
        }
    }

    @Test fun receiverCanPullPlaybackAndSourcePausesOnlyAfterCommit() = runTest {
        withContext(Dispatchers.IO) {
            val aInput = PipedInputStream(32_768)
            val bOutput = PipedOutputStream(aInput)
            val bInput = PipedInputStream(32_768)
            val aOutput = PipedOutputStream(bInput)
            val aResource = Closeable { aInput.close(); aOutput.close() }
            val bResource = Closeable { bInput.close(); bOutput.close() }
            val aId = "a".repeat(32)
            val bId = "b".repeat(32)
            val aPeer = NearbyPeer(aId, "手机", "a".repeat(64), "192.168.1.2", NEARBY_PORT, NEARBY_DEVICE_PHONE)
            val bPeer = NearbyPeer(bId, "平板", "b".repeat(64), "192.168.1.3", NEARBY_PORT, NEARBY_DEVICE_TABLET)
            var position = 12_000L
            var sourcePaused = false
            val source = NearbyDuplexConnection(bPeer, aResource, aInput, aOutput, aId) { _, request ->
                when (request.kind) {
                    "pull" -> NearbyMessage(
                        kind = "pull_ready",
                        id = aId,
                        transferId = request.transferId,
                        video = NearbyVideo("BV1xx411c7mD", 99, position, 1.25f, "接力视频", ""),
                        accepted = true,
                    )
                    "snapshot" -> NearbyMessage(
                        kind = "snapshot",
                        id = aId,
                        transferId = request.transferId,
                        video = NearbyVideo("BV1xx411c7mD", 99, position, 1.25f, "接力视频", ""),
                        accepted = true,
                    )
                    "pull_commit" -> {
                        if (request.accepted) sourcePaused = true
                        NearbyMessage(kind = "pull_committed", id = aId, transferId = request.transferId, accepted = request.accepted)
                    }
                    else -> error("unexpected ${request.kind}")
                }
            }
            val puller = NearbyDuplexConnection(aPeer, bResource, bInput, bOutput, bId) { _, _ ->
                error("puller does not handle requests in this test")
            }
            val sourceReader = launch(Dispatchers.IO) { try { source.run() } catch (_: IOException) {} }
            val pullerReader = launch(Dispatchers.IO) { try { puller.run() } catch (_: IOException) {} }
            try {
                withTimeout(5_000) {
                    val transferId = nearbyToken()
                    val ready = puller.request(NearbyMessage(kind = "pull", id = bId, transferId = transferId))
                    assertEquals("pull_ready", ready.kind)
                    assertEquals(12_000L, ready.video?.positionMs)
                    assertEquals(false, sourcePaused)
                    position = 18_500L
                    val latest = puller.request(NearbyMessage(kind = "snapshot", id = bId, transferId = transferId))
                    assertEquals(18_500L, latest.video?.positionMs)
                    assertEquals(false, sourcePaused)
                    val commit = puller.request(
                        NearbyMessage(kind = "pull_commit", id = bId, transferId = transferId, accepted = true)
                    )
                    assertEquals("pull_committed", commit.kind)
                    assertEquals(true, commit.accepted)
                    assertEquals(true, sourcePaused)
                }
            } finally {
                source.close(); puller.close()
                sourceReader.cancel(); pullerReader.cancel()
            }
        }
    }
}
