package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelatedVideoItemPolicyTest {

    @Test
    fun `shared transition mode keeps related card scale stable`() {
        assertEquals(
            1f,
            resolveRelatedVideoCardPressScaleTarget(
                isPressed = true,
                transitionEnabled = true
            )
        )
    }

    @Test
    fun `normal mode also keeps related card scale stable`() {
        assertEquals(
            1f,
            resolveRelatedVideoCardPressScaleTarget(
                isPressed = true,
                transitionEnabled = false
            )
        )
        assertEquals(
            1f,
            resolveRelatedVideoCardPressScaleTarget(
                isPressed = false,
                transitionEnabled = false
            )
        )
    }

    @Test
    fun `cover crossfade is disabled in all modes for list stability`() {
        assertFalse(shouldEnableRelatedVideoCoverCrossfade(transitionEnabled = true))
        assertFalse(shouldEnableRelatedVideoCoverCrossfade(transitionEnabled = false))
    }

    @Test
    fun `related cards preserve the parent detail route for nested navigation`() {
        assertEquals("video", resolveRelatedVideoSharedElementSourceRoute(null))
        assertEquals("video", resolveRelatedVideoSharedElementSourceRoute(""))
        assertEquals("video/BV1", resolveRelatedVideoSharedElementSourceRoute("video/BV1?from=related"))
        assertEquals("home", resolveRelatedVideoSharedElementSourceRoute("home"))
        // Source sessions remain host route + target bvid so nested return can restore history.
        assertEquals("video/BV_ENTRY", resolveRelatedVideoCardHostRoute("video/BV_ENTRY?cid=4", "BV_CHANGED"))
        assertEquals("video/BV_ENTRY", resolveRelatedVideoCardHostRoute("video/BV_ENTRY", "BV_CHANGED_AGAIN"))
        assertEquals("video/BV_CHANGED", resolveRelatedVideoCardHostRoute(null, "BV_CHANGED"))
    }



    @Test
    fun `related videos chunk into single column rows`() {
        val videos = (1..5).map { index ->
            RelatedVideo(
                aid = index.toLong(),
                bvid = "BV$index",
                title = "t$index",
                owner = Owner(),
                stat = Stat(),
            )
        }
        val rows = chunkRelatedVideosForHomeStyleGrid(videos)
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.size == 1 })
        assertEquals("BV5", rows.last().single().bvid)
    }

    @Test
    fun `related skeleton mirrors the single column transition geometry`() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/ui/components/SkeletonComponents.kt"
        ).readText()
        val relatedSkeleton = source
            .substringAfter("private fun RelatedVideoGridRowSkeleton()")
            .substringBefore("private fun RelatedVideoItemSkeleton(")

        assertTrue(relatedSkeleton.contains("coverAspectRatio = cardLayout.coverAspectRatio"))
        assertTrue(relatedSkeleton.contains("RelatedVideoItemSkeleton("))
        assertFalse(relatedSkeleton.contains("repeat(2)"))
    }

    @Test
    fun `related video cover uses shared CDN sizing policy`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/components/RelatedVideoItem.kt")
            .readText()

        assertTrue(source.contains("FormatUtils.resolveVideoCoverUrl(video.pic, useLowQuality = false)"))
        assertFalse(source.contains("FormatUtils.fixImageUrl(video.pic)"))
    }

    @Test
    fun `related video return media keeps the rounded cover clip through handoff`() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt"
        ).readText()

        assertTrue(source.contains("val returnMediaClipShape = AppShapes.mediaCover()"))
        assertTrue(
            Regex("videoDetailReturnMediaLayout\\([\\s\\S]*?clipShape = returnMediaClipShape")
                .findAll(source)
                .count() >= 2
        )
    }

    @Test
    fun `press haptic is disabled for related cards`() {
        assertFalse(
            shouldTriggerRelatedVideoPressHaptic(
                isPressed = true,
                transitionEnabled = true
            )
        )
        assertFalse(
            shouldTriggerRelatedVideoPressHaptic(
                isPressed = true,
                transitionEnabled = false
            )
        )
    }

    @Test
    fun `related cards expose more menu entry`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/components/RelatedVideoItem.kt")
            .readText()
        assertTrue(source.contains("onMoreClick: (() -> Unit)? = null"))
        assertTrue(source.contains("AppDropdownMenu("))
        assertFalse(source.contains("RelatedVideoActionSheet("))
    }
}
