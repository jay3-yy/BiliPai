@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.download

import java.io.File
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ResumableAssetDownloaderTest {
    @get:Rule val files = TemporaryFolder()
    private val content = "0123456789".toByteArray()
    private val ranges = Collections.synchronizedList(mutableListOf<String>())

    private fun client(
        ignoreRanges: Boolean = false,
        shortBody: Boolean = false,
        changedContent: ByteArray = content,
    ) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            assertEquals("https://www.bilibili.com/", request.header("Referer"))
            val range = request.header("Range")
            val bounds = range?.removePrefix("bytes=")?.split('-', limit = 2)
            if (range != null) ranges += range
            val start = bounds?.first()?.toInt() ?: 0
            val end = bounds?.get(1)?.toIntOrNull() ?: changedContent.lastIndex
            val partial = range != null && !ignoreRanges && request.method != "HEAD"
            val body = when {
                request.method == "HEAD" -> byteArrayOf()
                partial -> changedContent.copyOfRange(start, end + 1)
                shortBody -> changedContent.copyOf(3)
                else -> changedContent
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (partial) 206 else 200).message("OK")
                .header("Content-Length", if (request.method == "HEAD") content.size.toString() else body.size.toString())
                .header("Accept-Ranges", "bytes")
                .apply { if (partial) header("Content-Range", "bytes $start-$end/${content.size}") }
                .body(body.toResponseBody(null)).build()
        }.build()

    private fun request(output: File, chunks: Int = 1) = HttpDownloadAssetRequest(
        url = "https://media.example/video.m4s", outputFile = output,
        headers = mapOf("Referer" to "https://www.bilibili.com/"),
        parallelThresholdBytes = 1, maxParallelChunks = chunks,
    )

    private suspend fun leavePartialDownload(output: File) {
        try {
            ResumableAssetDownloader(client(shortBody = true)).download(request(output), {}, { _, _ -> })
            fail("A short response must leave a partial download")
        } catch (_: IOException) { }
    }

    @Test fun resumesExistingTemporaryBytesWithoutDuplicatingThePrefix() = runTest {
        val output = File(files.root, "video")
        leavePartialDownload(output)
        val result = ResumableAssetDownloader(client()).download(request(output), {}, { _, _ -> })
        assertArrayEquals(content, output.readBytes())
        assertEquals(10L, result.downloadedBytes)
        assertTrue(ranges.contains("bytes=3-"))
        assertFalse(File(files.root, "video.part").exists())
    }

    @Test fun sameLengthReplacementDoesNotReuseTheOldPrefix() = runTest {
        val output = File(files.root, "video")
        leavePartialDownload(output)
        val replacement = "9876543210".toByteArray()
        ResumableAssetDownloader(client(changedContent = replacement)).download(
            request(output).copy(url = "https://media.example/replacement.m4s"), {}, { _, _ -> },
        )
        assertArrayEquals(replacement, output.readBytes())
        assertFalse(ranges.contains("bytes=3-"))
    }

    @Test fun serverIgnoringParallelRangesFallsBackToASingleRequest() = runTest {
        val output = File(files.root, "video")
        val result = ResumableAssetDownloader(client(ignoreRanges = true))
            .download(request(output, chunks = 3), {}, { _, _ -> })
        assertEquals(1, result.segmentCount)
        assertTrue(ranges.isNotEmpty())
        assertArrayEquals(content, output.readBytes())
    }

    @Test fun mergesParallelChunksInFileOrder() = runTest {
        val output = File(files.root, "video")
        val result = ResumableAssetDownloader(client()).download(request(output, chunks = 3), {}, { _, _ -> })
        assertEquals(3, result.segmentCount)
        assertArrayEquals(content, output.readBytes())
        assertTrue(ranges.containsAll(listOf("bytes=0-3", "bytes=4-6", "bytes=7-9")))
    }

    @Test fun shortResponseKeepsThePartFileAndDoesNotPublishACompletedFile() = runTest {
        val output = File(files.root, "video")
        try {
            ResumableAssetDownloader(client(shortBody = true)).download(request(output), {}, { _, _ -> })
            fail("A short response must not complete a download")
        } catch (_: IOException) { }
        assertFalse(output.exists())
        assertArrayEquals(content.copyOf(3), File(files.root, "video.part").readBytes())
    }
}
