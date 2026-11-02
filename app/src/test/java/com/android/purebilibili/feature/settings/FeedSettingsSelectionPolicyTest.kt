package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.store.SettingsManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSettingsSelectionPolicyTest {

    @Test
    fun `resolveFeedApiSegmentOptions should preserve enum order and labels`() {
        val options = resolveFeedApiSegmentOptions()

        assertEquals(SettingsManager.FeedApiType.entries.size, options.size)
        assertEquals(SettingsManager.FeedApiType.WEB, options[0].value)
        assertEquals("网页端", options[0].label)
        assertEquals(SettingsManager.FeedApiType.MOBILE, options[1].value)
        assertEquals("手机端", options[1].label)
    }

    @Test
    fun `non glass Miuix settings use grouped cards while legacy paths remain flat`() {
        val source = locate("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsPageScaffold.kt")
            .readText()

        assertTrue(source.contains("LocalAppPreferenceGroupPresentation provides if (nonGlassMiuix)"))
        assertTrue(source.contains("AppPreferenceGroupPresentation.CARD"))
        assertTrue(source.contains("AppPreferenceGroupPresentation.FLAT"))
    }

    private fun locate(path: String): File {
        return listOf(File(path), File("app/$path"))
            .firstOrNull { it.exists() }
            ?: error("Cannot locate $path from cwd")
    }
}
