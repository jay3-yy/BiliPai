package com.android.purebilibili.feature.personal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PiliPlus 式个人列表卡选择遮罩结构约束：
 * 遮罩由 PersonalMediaCardFrame 提供组件，三张个人卡在封面覆盖层接入；
 * 旧的整卡勾选框 / 整卡选中底色不应再出现。
 */
class PersonalCardSelectMaskStructureTest {

    private fun readSource(relative: String): String = listOf(
        File("app/src/main/java/com/android/purebilibili/$relative"),
        File("src/main/java/com/android/purebilibili/$relative"),
    ).first(File::exists).readText()

    @Test
    fun frameProvidesSelectMaskWithoutCheckboxChrome() {
        val source = readSource("feature/personal/PersonalMediaCard.kt")
        assertTrue(source.contains("fun PersonalCardSelectMask("))
        assertFalse(source.contains("AppCheckbox("))
    }

    @Test
    fun personalCardsRenderMaskInsideCoverOverlay() {
        listOf(
            "feature/list/HistoryPersonalCard.kt",
            "feature/list/FavoritePersonalCard.kt",
            "feature/watchlater/WatchLaterScreen.kt",
        ).forEach { relative ->
            val source = readSource(relative)
            assertTrue(
                source.contains("PersonalCardSelectMask(selected"),
                "缺少选择遮罩接入: $relative",
            )
        }
    }

    @Test
    fun historyCardDropsWholeCardSelectionTintAndDoublePadding() {
        val source = readSource("feature/list/HistoryPersonalCard.kt")
        assertFalse(source.contains("primary.copy(alpha = 0.10f)"))
        assertFalse(source.contains("padding(horizontal = 12.dp, vertical = 5.dp)"))
    }
}
