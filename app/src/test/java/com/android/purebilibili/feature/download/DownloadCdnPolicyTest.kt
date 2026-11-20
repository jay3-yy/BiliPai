package com.android.purebilibili.feature.download

import com.android.purebilibili.data.model.response.DashVideo
import java.io.EOFException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCdnPolicyTest {
    @Test
    fun ordersDistinctUsableCandidatesWithUserPreferenceFirst() {
        val preferred = "https://preferred.bilivideo.com/video.m4s"
        val primary = "https://main.bilivideo.com/video.m4s"
        val backup = "https://backup.bilivideo.com/video.m4s?deadline=123"

        assertEquals(
            listOf(preferred, primary, backup),
            downloadCdnCandidates(preferred, primary, listOf(primary, "", backup, backup, "file:///tmp/video.m4s")),
        )
    }

    @Test
    fun keepsBackupsForTheSelectedCodecWhenThePrimaryHostWasRewritten() {
        val main = "https://main.bilivideo.com/upgcxcode"
        val backup = "https://backup.bilivideo.com/upgcxcode"
        val avc = DashVideo(id = 80, baseUrl = "$main/avc.m4s?deadline=1", backupUrl = listOf("$backup/avc.m4s"))
        val hevc = DashVideo(id = 80, baseUrl = "$main/hevc.m4s?deadline=1", backupUrl = listOf("$backup/hevc.m4s"))

        assertEquals(
            listOf(hevc.baseUrl, "$backup/hevc.m4s"),
            listOf(avc, hevc).downloadVideoBackupUrls("https://preferred.bilivideo.com/upgcxcode/hevc.m4s?deadline=1"),
        )
    }

    @Test
    fun invalidResponseOrTrackCanSwitchCdnButLocalWriteFailureCannot() {
        assertTrue(shouldSwitchDownloadCdn(EOFException("下载未完成")))
        assertTrue(shouldSwitchDownloadCdn(DownloadTrackIntegrityException(DownloadAssetKind.VIDEO, "缺少所需媒体轨")))
        assertFalse(shouldSwitchDownloadCdn(IOException("No space left on device")))
    }
}
