package com.android.purebilibili.feature.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadTrackIntegrityTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun unchangedBytesMatchSavedDigestButSameLengthDamageIsRejected() {
        val track = files.newFile()
        track.writeText("abc")
        val kind = DownloadAssetKind.VIDEO
        val hash = verifyDownloadTrackDigest(track, kind, 3L, expectedSha256 = null) {}

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        assertEquals(hash, verifyDownloadTrackDigest(track, kind, 3L, expectedSha256 = hash) {})

        track.writeText("abd")
        assertThrows(DownloadTrackIntegrityException::class.java) {
            verifyDownloadTrackDigest(track, kind, 3L, expectedSha256 = hash) {}
        }
    }

    @Test
    fun truncatedTrackIsRejectedEvenBeforeItsFirstDigest() {
        val track = files.newFile()
        track.writeText("ab")
        assertThrows(DownloadTrackIntegrityException::class.java) {
            verifyDownloadTrackDigest(track, DownloadAssetKind.AUDIO, 3L, expectedSha256 = null) {}
        }
    }
}
