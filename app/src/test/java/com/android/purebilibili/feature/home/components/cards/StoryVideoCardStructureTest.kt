package com.android.purebilibili.feature.home.components.cards

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryVideoCardStructureTest {



    @Test
    fun storyCoverContainerClipsPlaceholderAndImageTogether() {
        val source = File("src/main/java/com/android/purebilibili/feature/home/components/cards/StoryVideoCard.kt")
            .readText()
        val coverContainerSource = source
            .substringAfter("// 卡片容器 (封面)")
            .substringBefore("// 封面比例由首页卡片样式统一配置。")

        assertTrue(source.contains("val cardShape = AppShapes.container(ContainerLevel.ProminentCard)"))
        assertTrue(source.contains("val coverShape = cardShape"))
        assertTrue(coverContainerSource.contains(".testTag(\"home_story_video_cover\")"))
        assertTrue(coverContainerSource.contains(".aspectRatio(coverAspectRatio)"))
        assertTrue(coverContainerSource.contains(".clip(coverShape)"))
        assertTrue(coverContainerSource.contains(".background(MaterialTheme.colorScheme.surfaceVariant)"))
    }
}
