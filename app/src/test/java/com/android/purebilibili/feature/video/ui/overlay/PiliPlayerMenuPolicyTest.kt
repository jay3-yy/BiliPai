@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiliPlayerMenuPolicyTest {
    @Test fun preferredCodecChoicesKeepGlobalPreferencesAndMergeStreamProfiles() {
        assertEquals(listOf("avc1", "hev1", "av01"), resolvePiliCodecPreferenceOptions(emptyList()))
        assertEquals(
            listOf("avc1", "hev1", "av01", "dvh1"),
            resolvePiliCodecPreferenceOptions(
                listOf("avc1.640028", "AVC1.640032", "hvc1.1.6", "hev1.1.6", "av01.0.08", "", "dvh1.08.06"),
            ),
        )
    }
    @Test fun decoderChoicesExcludeOtherQualitiesWithoutDuplicatingFormats() {
        fun video(quality: Int, codec: String) =
            com.android.purebilibili.data.model.response.DashVideo(id = quality, codecs = codec)
        assertEquals(
            listOf("avc1.high", "hev1.main"),
            resolvePiliAvailableCodecIds(listOf(video(80, "avc1.high"), video(64, "av01.main"),
                video(80, "hev1.main"), video(80, "avc1.high"), video(80, ""))),
        )
        assertEquals(emptyList(), resolvePiliAvailableCodecIds(emptyList()))
    }
    @Test fun decoderStatusUsesTheSelectedVideoTrackRatherThanAudioOrAnUnselectedFormat() {
        fun group(id: String, mime: String, codec: String, selected: Boolean) =
            androidx.media3.common.Tracks.Group(
                androidx.media3.common.TrackGroup(
                    id, androidx.media3.common.Format.Builder()
                        .setSampleMimeType(mime).setCodecs(codec).build(),
                ),
                false, intArrayOf(androidx.media3.common.C.FORMAT_HANDLED), booleanArrayOf(selected),
            )
        val tracks = androidx.media3.common.Tracks(listOf(
            group("audio", "audio/mp4a-latm", "mp4a.40.2", true),
            group("avc", "video/avc", "avc1.640028", false),
            group("hevc", "video/hevc", "hev1.1.6.L120.90", true),
        ))
        assertEquals("hev1.1.6.L120.90", resolvePiliSelectedVideoCodec(tracks))
        assertEquals(null, resolvePiliSelectedVideoCodec(androidx.media3.common.Tracks.EMPTY))
    }
    @Test fun amplificationUsesLinearGainInFivePercentSteps() {
        assertEquals(1f, normalizePlayerAmplification(0f))
        assertEquals(3f, normalizePlayerAmplification(4f))
        assertEquals(1.25f, normalizePlayerAmplification(1.26f))
        assertEquals(1f, normalizePlayerAmplification(Float.NaN))
        assertEquals(0, playerAmplificationMillibels(1f))
        assertEquals(602, playerAmplificationMillibels(2f))
        assertEquals(954, playerAmplificationMillibels(3f))
    }
    @Test fun sourceEditRequiresValidVideoButAllowsRemovingSeparateAudio() {
        assertTrue(isEditablePlaybackUrl("https://example.com/video.m4s?sign=a%2Fb"))
        assertFalse(isEditablePlaybackUrl(""))
        assertTrue(isEditablePlaybackUrl("", allowEmpty = true))
        assertFalse(isEditablePlaybackUrl("javascript:alert(1)"))
        assertFalse(isEditablePlaybackUrl("https:///missing-host"))
    }
    @Test fun sourceReplacementMustMatchBothVideoAndPartIdentity() {
        assertTrue(matchesPiliPlaybackIdentity("BV1", 1, "BV1", 1))
        assertFalse(matchesPiliPlaybackIdentity("BV1", 1, "BV2", 1))
        assertFalse(matchesPiliPlaybackIdentity("BV1", 1, "BV1", 2))
    }
    @Test fun cdnServicesAreRealReferencePoliciesAndKeepSignedUrlBytes() {
        val signed = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/00/11/video.m4s?e=ig8eux&sign=a%2Fb%2Bz"
        assertEquals(signed, selectPiliCdnUrl(listOf(signed), PiliCdnService.BASE))
        assertEquals(signed, selectPiliCdnUrl(listOf(signed), PiliCdnService.BACKUP))
        assertEquals(signed.replace("upos-sz-mirrorcos.bilivideo.com", "upos-sz-mirrorali.bilivideo.com"),
            selectPiliCdnUrl(listOf(signed), PiliCdnService.ALI))
    }
    @Test fun backupSearchSkipsMcdnBaseAndFindsActualMirrorBackup() {
        val base = "https://1.2.3.4:4483/v1/resource/abc?sign=x"
        val backup = "https://upos-sz-mirrorhw.bilivideo.com/upgcxcode/abc/video.m4s?sign=y"
        assertEquals(base, selectPiliCdnUrl(listOf(base, backup), PiliCdnService.BASE))
        assertEquals(backup, selectPiliCdnUrl(listOf(base, backup), PiliCdnService.BACKUP))
        assertEquals(backup.replace("upos-sz-mirrorhw.bilivideo.com", "upos-sz-mirrorcos.bilivideo.com"),
            selectPiliCdnUrl(listOf(base, backup), PiliCdnService.COS))
    }
    @Test fun backupSelectionPreservesAllRiskySignedUrlWhenNoRegularMirrorExists() {
        val source = "https://1.2.3.4:4483/v1/resource/abc?sign=x%2Fy"
        assertEquals(source, selectPiliCdnUrl(listOf(source), PiliCdnService.BACKUP))
    }
    @Test fun mcdnOnlySourceUsesReferenceProxyRatherThanInventedMirrorPath() {
        val source = "https://1.2.3.4:4483/v1/resource/abc?sign=x"
        assertTrue(selectPiliCdnUrl(listOf(source), PiliCdnService.HW)
            .startsWith("https://proxy-tf-all-ws.bilivideo.com?url=https%3A%2F%2F"))
    }
}
