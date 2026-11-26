package com.android.purebilibili.feature.nearby

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NearbyProtocolTest {
    private val video = NearbyVideo("BV1xx411c7mD", 1234, 43_210, 1.5f, "接力视频", "https://i0.hdslb.com/test.jpg")

    @Test fun playbackMetadataRoundTripsWithoutCredentials() {
        val message = NearbyMessage(kind = "video", id = "a".repeat(32), video = video)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).writeNearby(message)
        val decoded = DataInputStream(ByteArrayInputStream(buffer.toByteArray())).readNearby()
        assertEquals(message, decoded)
        decoded.video!!.validate()
        val text = buffer.toString(Charsets.UTF_8.name())
        assertTrue(!text.contains("cookie", ignoreCase = true))
        assertTrue(!text.contains("sessdata", ignoreCase = true))
    }

    @Test fun legacyMessagesAndOversizedCorrelationIdsAreRejected() {
        val original = NearbyMessage(kind = "hello", id = "a".repeat(32))
        for (invalid in listOf(
            original.copy(version = 1),
            original.copy(version = 2),
            original.copy(requestId = "x".repeat(33)),
            original.copy(replyTo = "x".repeat(33)),
            original.copy(transferId = "x".repeat(33)),
            original.copy(deviceType = "x".repeat(17)),
        )) {
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).writeNearby(invalid)
            assertFails { DataInputStream(ByteArrayInputStream(buffer.toByteArray())).readNearby() }
        }
    }

    @Test fun invalidFrameSizesAreRejectedBeforeAllocation() {
        for (size in listOf(-1, 0, NEARBY_MAX_FRAME_BYTES + 1, Int.MAX_VALUE)) {
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).writeInt(size)
            assertFailsWith<IllegalArgumentException> {
                DataInputStream(ByteArrayInputStream(buffer.toByteArray())).readNearby()
            }
        }
        assertFailsWith<EOFException> {
            DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0, 0, 20, 1))).readNearby()
        }
    }

    @Test fun invalidPlaybackStateCannotReachThePlayer() {
        for (invalid in listOf(
            video.copy(bvid = "invalid"), video.copy(cid = 0), video.copy(positionMs = -1),
            video.copy(positionMs = Long.MAX_VALUE), video.copy(speed = Float.NaN),
            video.copy(speed = Float.POSITIVE_INFINITY), video.copy(speed = 0f),
            video.copy(title = "x".repeat(201)), video.copy(cover = "https://example.com/image.jpg"),
            video.copy(cover = "https://i0.hdslb.com:8888/image.jpg"),
        )) assertFails { invalid.validate() }
    }

    @Test fun onlyLiteralLocalAddressesAreAccepted() {
        for (host in listOf("192.168.1.3", "10.0.2.2", "127.0.0.1", "::1", "fd12::1")) requireLocalAddress(host)
        for (host in listOf("example.com", "dead.beef", "8.8.8.8", "0.0.0.0", "224.0.0.1", "999.1.1.1", "127.1")) {
            assertFails { requireLocalAddress(host) }
        }
    }

    @Test fun coversAreNormalizedWithoutAllowingArbitraryEndpoints() {
        assertEquals("https://i0.hdslb.com/test.jpg", normalizeNearbyCover("http://i0.hdslb.com/test.jpg"))
        assertEquals("https://i0.hdslb.com/test.jpg", normalizeNearbyCover("//i0.hdslb.com/test.jpg"))
        for (cover in listOf("file:///data/private", "https://evilhdslb.com/a", "https://i0.hdslb.com@evil.com/a")) {
            assertEquals("", normalizeNearbyCover(cover))
        }
    }

    @Test fun pairingTokensAndPinsAreIndependentAndFixedLength() {
        val token = nearbyToken()
        assertTrue(Regex("[a-f0-9]{32}").matches(token))
        assertNotEquals(token, nearbyToken())
        assertEquals(64, nearbyPin(byteArrayOf(1, 2, 3)).length)
        assertNotEquals(nearbyPin(byteArrayOf(1)), nearbyPin(byteArrayOf(2)))
    }

    @Test fun bangumiAndLiveMediaMetadataValidateAndRoundTrip() {
        val bangumi = NearbyVideo(
            mediaType = NEARBY_MEDIA_BANGUMI,
            seasonId = 45678L,
            epId = 98765L,
            positionMs = 120_000L,
            speed = 1.25f,
            title = "测试番剧 · 第1话",
            cover = "https://i0.hdslb.com/bangumi.jpg",
        )
        val live = NearbyVideo(
            mediaType = NEARBY_MEDIA_LIVE,
            roomId = 223344L,
            uname = "测试主播",
            title = "测试直播间",
            cover = "https://i0.hdslb.com/live.jpg",
        )
        for (media in listOf(bangumi, live)) {
            val message = NearbyMessage(kind = "presence", id = "a".repeat(32), deviceType = NEARBY_DEVICE_TABLET, video = media)
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).writeNearby(message)
            val decoded = DataInputStream(ByteArrayInputStream(buffer.toByteArray())).readNearby()
            assertEquals(message, decoded)
            decoded.video!!.validate()
        }
        assertTrue(bangumi.sameContentAs(bangumi.copy(positionMs = 240_000L)))
        assertTrue(live.sameContentAs(live.copy(title = "改了标题")))
        assertFails { bangumi.copy(epId = 0L).validate() }
        assertFails { live.copy(roomId = 0L).validate() }
        assertFails { video.copy(mediaType = "unknown").validate() }
    }

    @Test fun playlistQueueContextRoundTripsAndEnforcesBounds() {
        val queue = NearbyQueueContext(
            items = listOf(
                NearbyQueueItem(
                    bvid = "BV1xx411c7mD",
                    cid = 1234L,
                    title = "P1 开篇",
                    cover = "https://i0.hdslb.com/p1.jpg",
                    owner = "测试UP",
                    duration = 320L,
                ),
                NearbyQueueItem(
                    bvid = "BV1xx411c7mD",
                    cid = 1235L,
                    title = "P2 进阶",
                    cover = "https://i0.hdslb.com/p2.jpg",
                    owner = "测试UP",
                    duration = 480L,
                ),
            ),
            currentIndex = 1,
            playMode = "SEQUENTIAL",
            isExternal = true,
            externalSource = "SEASON",
        )
        val withQueue = video.copy(queue = queue)
        val message = NearbyMessage(kind = "video", id = "a".repeat(32), video = withQueue)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).writeNearby(message)
        val decoded = DataInputStream(ByteArrayInputStream(buffer.toByteArray())).readNearby()
        assertEquals(message, decoded)
        decoded.video!!.validate()

        val tooMany = queue.copy(
            items = List(NEARBY_MAX_QUEUE_ITEMS + 1) { idx ->
                NearbyQueueItem(bvid = "BV1xx411c7mD", cid = 1000L + idx, title = "P$idx")
            }
        )
        assertFails { video.copy(queue = tooMany).validate() }
        assertFails { video.copy(queue = queue.copy(currentIndex = 5)).validate() }
        assertFails {
            video.copy(
                queue = queue.copy(
                    items = listOf(NearbyQueueItem(bvid = "invalid", cid = 1L))
                )
            ).validate()
        }
        assertFails {
            video.copy(
                queue = queue.copy(
                    items = listOf(NearbyQueueItem(bvid = "BV1xx411c7mD", cid = 1L, cover = "https://evil.example/a.jpg"))
                )
            ).validate()
        }
    }

    @Test fun deviceNameSanitizerStripsControlCharsAndCapsLength() {
        assertEquals("客厅平板", sanitizeNearbyDeviceName("  客厅平板 \n "))
        assertEquals("我的\t设备".replace("\t", " "), sanitizeNearbyDeviceName("我的\t设备"))
        assertEquals(24, sanitizeNearbyDeviceName("很".repeat(40)).length)
        assertEquals("", sanitizeNearbyDeviceName("   \r\n  "))
        assertEquals("", sanitizeNearbyDeviceName(null))
    }

    @Test fun qualityAndDanmakuPreferencesRoundTripAndValidate() {
        val media = video.copy(
            speed = 1.5f,
            qualityId = 120,
            qualityLabel = "4K",
            danmakuEnabled = false,
            audioQualityId = 30250,
            audioQualityLabel = "杜比全景声",
            audioLang = "en",
            audioLangLabel = "AI 英语",
        )
        val message = NearbyMessage(kind = "video", id = "a".repeat(32), video = media)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).writeNearby(message)
        val decoded = DataInputStream(ByteArrayInputStream(buffer.toByteArray())).readNearby()
        assertEquals(message, decoded)
        decoded.video!!.validate()
        assertEquals("4K · 杜比全景声 · AI 英语 · 弹幕关", formatNearbyPreferenceSummary(decoded.video!!))
        assertEquals("4K · 杜比全景声 · AI 英语 · 1.5x · 弹幕关", formatNearbyPreferenceSummary(decoded.video!!, includeSpeed = true))
        assertEquals("1080P60", resolveNearbyQualityLabel(116))
        assertEquals("原画", resolveNearbyQualityLabel(10000, isLive = true))
        assertEquals("Hi-Res", resolveNearbyAudioQualityLabel(30251))
        assertEquals("1.25x", formatNearbySpeed(1.25f))
        assertEquals("2x", formatNearbySpeed(2.0f))
        assertEquals("", resolveNearbyQualityLabel(0, rawLabel = "自动"))

        assertFails { video.copy(qualityId = -1).validate() }
        assertFails { video.copy(qualityId = 100_001).validate() }
        assertFails { video.copy(qualityLabel = "x".repeat(25)).validate() }
        assertFails { video.copy(audioQualityId = -1).validate() }
        assertFails { video.copy(audioQualityLabel = "x".repeat(25)).validate() }
        assertFails { video.copy(audioLang = "x".repeat(17)).validate() }
        assertFails { video.copy(audioLangLabel = "x".repeat(25)).validate() }

        val audioOnly = video.copy(audioOnly = true, audioQualityId = 30251, audioQualityLabel = "Hi-Res")
        assertEquals("听视频", audioOnly.mediaTypeLabel)
        assertTrue(audioOnly.sameContentAs(video))
        val audioMsg = NearbyMessage(kind = "video", id = "a".repeat(32), video = audioOnly)
        val audioBuf = ByteArrayOutputStream()
        DataOutputStream(audioBuf).writeNearby(audioMsg)
        val audioDecoded = DataInputStream(ByteArrayInputStream(audioBuf.toByteArray())).readNearby()
        assertEquals(audioMsg, audioDecoded)
        assertTrue(audioDecoded.video!!.audioOnly)
    }
}
