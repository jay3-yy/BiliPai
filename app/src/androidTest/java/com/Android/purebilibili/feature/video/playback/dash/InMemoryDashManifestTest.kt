package com.android.purebilibili.feature.video.playback.dash

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import com.android.purebilibili.core.player.dash.buildLocalDashManifest
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.SegmentBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class InMemoryDashManifestTest {
    @Test
    fun qualityChangesKeepEachSourcesTracksAndAbsoluteCdnUrlsIsolated() {
        fun factory(quality: Int, host: String) = createInMemoryDashManifestDataSourceFactory(
            buildLocalDashManifest(
                durationMs = 60_000L,
                minBufferTimeMs = 1_500L,
                videoTracks = listOf(
                    DashVideo(
                        id = quality,
                        baseUrl = "https://$host/video.m4s?token=one&quality=$quality",
                        bandwidth = 4_000_000,
                        mimeType = "video/mp4",
                        codecs = "avc1.640028",
                        width = 1920,
                        height = 1080,
                        frameRate = "30",
                        segmentBase = SegmentBase("0-999", "1000-1999"),
                    ),
                ),
                audioTracks = listOf(
                    DashAudio(
                        id = 30280,
                        baseUrl = "https://$host/audio.m4s",
                        bandwidth = 192_000,
                        mimeType = "audio/mp4",
                        codecs = "mp4a.40.2",
                        segmentBase = SegmentBase("0-555", "556-999"),
                    ),
                ),
            ),
        )

        val original = factory(80, "original.example.com")
        val replacement = factory(64, "replacement.example.com")
        val originalManifest = parse(original)
        val replacementManifest = parse(replacement)
        // 切画质创建新媒体源后，旧源再次加载仍必须读取自己的清单。
        val reloadedOriginal = parse(original)

        assertTracks(originalManifest, 80, "original.example.com")
        assertTracks(replacementManifest, 64, "replacement.example.com")
        assertTracks(reloadedOriginal, 80, "original.example.com")
    }

    private fun parse(factory: DataSource.Factory): DashManifest {
        val uri = Uri.parse(IN_MEMORY_DASH_MANIFEST_URI)
        return DataSourceInputStream(
            factory.createDataSource(),
            DataSpec.Builder().setUri(uri).build(),
        ).use { stream -> DashManifestParser().parse(uri, stream) }
    }

    private fun assertTracks(manifest: DashManifest, quality: Int, host: String) {
        assertFalse(manifest.dynamic)
        assertEquals(60_000L, manifest.durationMs)
        val sets = manifest.getPeriod(0).adaptationSets
        assertEquals(2, sets.size)
        val video = sets[0].representations.single()
        assertEquals(quality.toString(), video.format.id)
        assertEquals("https://$host/video.m4s?token=one&quality=$quality", video.baseUrls[0].url)
        assertEquals(0L, video.getInitializationUri()!!.start)
        assertEquals(1000L, video.getInitializationUri()!!.length)
        val audio = sets[1].representations.single()
        assertEquals("30280", audio.format.id)
        assertEquals("https://$host/audio.m4s", audio.baseUrls[0].url)
        assertEquals(556L, audio.getInitializationUri()!!.length)
    }
}
