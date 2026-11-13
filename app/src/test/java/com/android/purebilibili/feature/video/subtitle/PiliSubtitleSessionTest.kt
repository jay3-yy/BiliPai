package com.android.purebilibili.feature.video.subtitle

import com.android.purebilibili.feature.video.ui.components.PlayerSubtitleAppearance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiliSubtitleSessionTest {
    private fun nativeTrack(language: String) = SubtitleTrackMeta(
        lan = language, lanDoc = language,
        subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/$language.json",
    )

    @Test fun lastConsumerReleasesDownloadedTextButPreservesImportedDocumentsAndSelection() {
        val session = PiliSubtitleSession(SubtitleDocumentIdentity("BVhistory", 1))
        val primary = nativeTrack("zh-Hans")
        val secondary = nativeTrack("en")
        session.acquiredTracks = listOf(primary, secondary)
        session.acquiredInitialized = true
        session.append("local.srt", listOf(SubtitleCue(0, 1000, "导入的字幕")))
        val imported = session.importedTracks.single()
        session.selectedTrackKey = primary.trackKey
        session.acquiredSecondaryTrackKey = secondary.trackKey
        session.acquiredDisplayMode = SubtitleDisplayMode.BILINGUAL
        session.timeOffsetMs = 250
        session.largeTextEnabled = true
        session.retainDownloadedCues()
        session.retainDownloadedCues()
        val loaded = mapOf(primary.trackKey to listOf(SubtitleCue(0, 1000, "下载的字幕")))
        assertTrue(session.mergeAcquiredCues(loaded, session.downloadedCueGeneration))
        session.releaseDownloadedCues()
        session.trimUnusedDownloadedCues()
        assertEquals(loaded, session.acquiredCues)
        session.releaseDownloadedCues()
        session.trimUnusedDownloadedCues()
        assertTrue(session.acquiredCues.isEmpty())
        assertSame(imported, session.importedTracks.single())
        assertEquals(primary.trackKey, session.selectedTrackKey)
        assertEquals(secondary.trackKey, session.acquiredSecondaryTrackKey)
        assertEquals(SubtitleDisplayMode.BILINGUAL, session.acquiredDisplayMode)
        assertEquals(250L, session.timeOffsetMs)
        assertTrue(session.largeTextEnabled)
        assertTrue(session.acquiredInitialized)
    }

    @Test fun fullscreenHandoffRetainsTextAndRetiredDownloadsCannotRefillAnInactiveSession() {
        val session = PiliSubtitleSession(SubtitleDocumentIdentity("BVhandoff", 1))
        val cues = mapOf("track" to listOf(SubtitleCue(0, 1000, "字幕")))
        session.retainDownloadedCues()
        val generation = session.downloadedCueGeneration
        session.mergeAcquiredCues(cues, generation)
        session.releaseDownloadedCues()
        // 全屏页面在队列中的清理执行前接手同一会话。
        session.retainDownloadedCues()
        session.trimUnusedDownloadedCues()
        assertEquals(cues, session.acquiredCues)
        assertEquals(generation, session.downloadedCueGeneration)
        session.releaseDownloadedCues()
        assertFalse(session.mergeAcquiredCues(cues, generation))
        session.trimUnusedDownloadedCues()
        session.retainDownloadedCues()
        assertFalse(session.mergeAcquiredCues(cues, generation))
        assertTrue(session.acquiredCues.isEmpty())
        assertTrue(session.mergeAcquiredCues(cues, session.downloadedCueGeneration))
    }

    @Test fun restorationLoadsOnlyMissingSelectedNativeTracksAndNeverReinitializesImports() {
        val session = PiliSubtitleSession(SubtitleDocumentIdentity("BVrestore", 1))
        val primary = nativeTrack("en")
        val secondary = nativeTrack("zh-Hans")
        val unused = nativeTrack("ja")
        session.acquiredTracks = listOf(unused, secondary, primary)
        session.selectedTrackKey = primary.trackKey
        session.acquiredSecondaryTrackKey = secondary.trackKey
        assertEquals(listOf(primary, secondary), session.missingSelectedNativeTracks())
        session.retainDownloadedCues()
        session.mergeAcquiredCues(
            mapOf(primary.trackKey to listOf(SubtitleCue(0, 1000, "primary"))), session.downloadedCueGeneration,
        )
        assertEquals(listOf(secondary), session.missingSelectedNativeTracks())
        session.append("local.srt", listOf(SubtitleCue(0, 1000, "imported")))
        assertTrue(session.missingSelectedNativeTracks().isEmpty())
    }

    @Test fun acquiredNativeCuesStaySeparateFromImportedDocuments() {
        val session = PiliSubtitleSession(SubtitleDocumentIdentity("BVnative", 100))
        val native = SubtitleTrackMeta(lan = "zh-Hans", lanDoc = "中文",
            subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/real.json")
        val nativeCues = listOf(SubtitleCue(0, 2000, "native"))
        session.acquiredTracks = listOf(native)
        session.retainDownloadedCues()
        session.mergeAcquiredCues(mapOf(native.trackKey to nativeCues), session.downloadedCueGeneration)
        session.selectedTrackKey = native.trackKey
        session.append("local.srt", listOf(SubtitleCue(0, 2000, "local")))
        assertSame(nativeCues, session.acquiredCues[native.trackKey])
        assertEquals(native.subtitleUrl, session.acquiredTracks.single().subtitleUrl)
        assertNotEquals(native.trackKey, session.selectedTrackKey)
        assertEquals(1, session.importedTracks.size)
    }

    @Test fun importedTrackContainsRealCuesAndIsSelected() {
        val session = PiliSubtitleSession(SubtitleDocumentIdentity("BVtest", 42))
        val cues = listOf(SubtitleCue(1000, 2000, "Actual document text"))
        session.append("captions.srt", cues)
        val track = session.importedTracks.single()
        assertEquals("captions.srt", track.name)
        assertSame(cues, track.cues)
        assertEquals(track.key, session.selectedTrackKey)
        session.append("second.ass", cues)
        assertNotEquals(track.key, session.selectedTrackKey)
        assertEquals(2, session.importedTracks.size)
    }

    @Test fun sessionsSurviveModeChangesButNeverLeakAcrossParts() {
        val first = PiliSubtitleSessions.get("BVidentitytest", 101)
        first.append("local.vtt", listOf(SubtitleCue(0, 1000, "first")))
        first.timeOffsetMs = 250
        assertSame(first, PiliSubtitleSessions.get("BVidentitytest", 101))
        val next = PiliSubtitleSessions.get("BVidentitytest", 102)
        assertNull(next.selectedTrackKey)
        assertEquals(0, next.importedTracks.size)
        assertEquals(0L, next.timeOffsetMs)
    }

    @Test fun appearanceUsesReferenceDefaultsAndBounds() {
        assertEquals(PlayerSubtitleAppearance(), normalizeSubtitleAppearance(PlayerSubtitleAppearance()))
        val clamped = normalizeSubtitleAppearance(PlayerSubtitleAppearance(
            inlineFontScale = 9f, fullscreenFontScale = -1f, fontWeight = 20, strokeWidth = 8f,
            horizontalPadding = -10, bottomPadding = 300, backgroundOpacity = -1f
        ))
        assertEquals(PlayerSubtitleAppearance(2.5f, 0.5f, 9, 5f, 0, 200, 0f), clamped)
    }
}
