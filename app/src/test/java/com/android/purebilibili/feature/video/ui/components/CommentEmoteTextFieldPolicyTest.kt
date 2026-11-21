package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentEmoteTextFieldPolicyTest {

    @Test
    fun unspecifiedColorsFallBackToThemeColorInsteadOfTransparentZero() {
        val fallback = Color(0xFFEAEAEA)
        val explicit = Color(0xFF123456)

        assertEquals(fallback, resolveCommentEmoteTextColor(Color.Unspecified, fallback))
        assertEquals(explicit, resolveCommentEmoteTextColor(explicit, fallback))
    }

    @Test
    fun unspecifiedFontSizeFallsBackToReadableDefault() {
        assertEquals(16.sp, resolveCommentEmoteFontSize(TextUnit.Unspecified))
        assertEquals(18.sp, resolveCommentEmoteFontSize(18.sp))
    }

    @Test
    fun selectionIsClampedToMaxEditableLength() {
        assertEquals(TextRange(0, 1000), clampCommentEmoteSelection(-4, 1200, 1000))
        assertEquals(TextRange(5, 12), clampCommentEmoteSelection(5, 12, 20))
        assertEquals(TextRange.Zero, clampCommentEmoteSelection(10, 10, 0))
    }

    @Test
    fun nativeEditorsOptOutOfForceDarkAndDynamicComposerProvidesExplicitTextColor() {
        val emoteField = loadSource("feature/video/ui/components/CommentEmoteTextField.kt")
        val liveEditor = loadSource("feature/live/components/LiveRichDanmakuEditor.kt")
        val dynamicComposer = loadSource("feature/dynamic/components/DynamicPublishComposer.kt")

        assertTrue(emoteField.contains("resolveCommentEmoteTextColor("))
        assertTrue(emoteField.contains("resolveCommentEmoteFontSize("))
        assertTrue(emoteField.contains("clampCommentEmoteSelection("))
        assertTrue(emoteField.contains("isForceDarkAllowed = false"))
        assertTrue(liveEditor.contains("isForceDarkAllowed = false"))
        assertTrue(dynamicComposer.contains("color = AppContentColors.primaryText()"))
        assertTrue(dynamicComposer.contains("textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppContentColors.primaryText())"))
    }

    private fun loadSource(relativePath: String): String {
        val path = "src/main/java/com/android/purebilibili/$relativePath"
        return listOf(File(path), File("app/$path")).first { it.exists() }.readText()
    }
}
