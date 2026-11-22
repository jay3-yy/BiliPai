package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuPoolSheetLayoutPolicyTest {

    @Test
    fun `danmaku pool search row matches item row height and uses thin border`() {
        assertEquals(40.dp, DANMAKU_POOL_ROW_MIN_HEIGHT)
        assertEquals(
            DANMAKU_POOL_ROW_INNER_HEIGHT + DANMAKU_POOL_ROW_VERTICAL_PADDING * 2,
            DANMAKU_POOL_ROW_MIN_HEIGHT
        )
        assertEquals(0.5.dp, DANMAKU_POOL_SEARCH_BORDER_WIDTH)
    }

    @Test
    fun `danmaku pool sheet replaces outlined text field with compact search bar`() {
        val source = loadDanmakuPoolSheetSource()

        assertFalse(
            "search bar should not use tall AppOutlinedTextField",
            source.contains("AppOutlinedTextField")
        )
        assertTrue(
            "search bar should use DanmakuPoolSearchBar",
            source.contains("DanmakuPoolSearchBar(")
        )
        assertTrue(
            "compact search bar should render via BasicTextField",
            source.contains("BasicTextField(")
        )
        assertTrue(
            "compact search bar should match card bordered shape",
            source.contains("AppShapes.borderedContainer(ContainerLevel.Card)")
        )
        assertTrue(
            "compact search bar should use thin border width",
            source.contains("BorderStroke(DANMAKU_POOL_SEARCH_BORDER_WIDTH, borderColor)")
        )
    }

    @Test
    fun `danmaku pool search bar and item row share height constants and avoid min touch expansion`() {
        val source = loadDanmakuPoolSheetSource()
        val searchBarBlock = source
            .substringAfter("private fun DanmakuPoolSearchBar(")
            .substringBefore("private fun DanmakuPoolItemRow(")
        val itemRowBlock = source.substringAfter("private fun DanmakuPoolItemRow(")

        assertTrue(
            "search bar should constrain height using DANMAKU_POOL_ROW_MIN_HEIGHT",
            searchBarBlock.contains("DANMAKU_POOL_ROW_MIN_HEIGHT")
        )
        assertTrue(
            "search bar should use DANMAKU_POOL_ROW_VERTICAL_PADDING",
            searchBarBlock.contains("DANMAKU_POOL_ROW_VERTICAL_PADDING")
        )
        assertFalse(
            "search bar clear action must not use AppIconButton which can expand to 48dp",
            searchBarBlock.contains("AppIconButton")
        )
        assertTrue(
            "item row should enforce DANMAKU_POOL_ROW_MIN_HEIGHT",
            itemRowBlock.contains("DANMAKU_POOL_ROW_MIN_HEIGHT")
        )
        assertTrue(
            "item row should use DANMAKU_POOL_ROW_VERTICAL_PADDING",
            itemRowBlock.contains("DANMAKU_POOL_ROW_VERTICAL_PADDING")
        )
    }

    private fun loadDanmakuPoolSheetSource(): String {
        val workspaceRoot = generateSequence(
            Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        ) { current ->
            current.parent
        }.first { candidate ->
            Files.exists(
                candidate.resolve(
                    "app/src/main/java/com/android/purebilibili/feature/video/ui/components/DanmakuPoolSheet.kt"
                )
            )
        }
        return workspaceRoot.resolve(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/DanmakuPoolSheet.kt"
        ).readText()
    }
}
