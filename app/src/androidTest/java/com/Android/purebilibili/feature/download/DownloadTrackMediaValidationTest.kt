package com.android.purebilibili.feature.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadTrackMediaValidationTest {
    @Test
    fun videoOnlyFilePassesVideoValidationAndFailsAudioValidation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("download-track", ".m4s", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open("api28_surface_rebind.mp4").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            requireReadableDownloadTrack(file, DownloadAssetKind.VIDEO)
            assertThrows(DownloadTrackIntegrityException::class.java) {
                requireReadableDownloadTrack(file, DownloadAssetKind.AUDIO)
            }
        } finally {
            file.delete()
        }
    }
}
