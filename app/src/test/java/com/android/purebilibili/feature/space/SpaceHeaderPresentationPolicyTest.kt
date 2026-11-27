package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.RelationStatData
import com.android.purebilibili.data.model.response.SpaceTagItem
import com.android.purebilibili.data.model.response.UpStatData
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaceHeaderPresentationPolicyTest {

    @Test
    fun `header metrics keep compact relation and likes set`() {
        val metrics = resolveSpaceHeaderMetricItems(
            relationStat = RelationStatData(following = 24, follower = 1024),
            upStat = UpStatData(likes = 42_000)
        )

        assertEquals(
            listOf("粉丝", "关注", "获赞"),
            metrics.map { it.label }
        )
        assertEquals(
            listOf(1024L, 24L, 42_000L),
            metrics.map { it.value }
        )
    }

    @Test
    fun `header metrics distinguish missing stats from real zero`() {
        val metrics = resolveSpaceHeaderMetricItems(
            relationStat = null,
            upStat = null
        )

        assertEquals(listOf(null, null, null), metrics.map { it.value })
        val zero = resolveSpaceHeaderMetricItems(RelationStatData(), UpStatData())
        assertEquals(listOf(0L, 0L, 0L), zero.map { it.value })
        val partial = resolveSpaceHeaderMetricItems(RelationStatData(follower = 12), null)
        assertEquals(listOf(12L, 0L, null), partial.map { it.value })
    }

    @Test
    fun `pinned top chrome stays clear at rest and solid after the header scrolls`() {
        assertEquals(
            0f,
            resolveSpacePinnedTopChromeScrim(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            )
        )
        assertEquals(
            0.5f,
            resolveSpacePinnedTopChromeScrim(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 60,
            )
        )
        assertEquals(
            1f,
            resolveSpacePinnedTopChromeScrim(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
            )
        )
    }

    @Test
    fun `resolveSpaceFollowActionLabel maps relations and owner aligned with PiliPlus`() {
        assertEquals("编辑资料", resolveSpaceFollowActionLabel(isOwner = true))
        assertEquals("关注", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 0, isFollowed = false))
        assertEquals("关注", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 2, isFollowed = false))
        assertEquals("移除黑名单", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 128, isFollowed = false))
        assertEquals("已关注", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 0, isFollowed = true))
        assertEquals("悄悄关注", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 1, isFollowed = true))
        assertEquals("已关注", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 2, isFollowed = true))
        assertEquals("已互关", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 4, isFollowed = true))
        assertEquals("已互关", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = 6, isFollowed = true))
        assertEquals("特别关注", resolveSpaceFollowActionLabel(isOwner = false, relationStatus = -10, isFollowed = true))
    }

    @Test
    fun `resolveSpaceBannerAlignment creates BiasAlignment with clamped dy`() {
        val center = resolveSpaceBannerAlignment(0f) as androidx.compose.ui.BiasAlignment
        assertEquals(0f, center.horizontalBias)
        assertEquals(0f, center.verticalBias)

        val top = resolveSpaceBannerAlignment(-0.8f) as androidx.compose.ui.BiasAlignment
        assertEquals(-0.8f, top.verticalBias, 0.001f)

        val clamped = resolveSpaceBannerAlignment(2.5f) as androidx.compose.ui.BiasAlignment
        assertEquals(1f, clamped.verticalBias, 0.001f)
    }

    @Test
    fun `resolveSpaceBannerColorFilter returns null when hasFilter is false`() {
        kotlin.test.assertNull(resolveSpaceBannerColorFilter(isLight = true, hasFilter = false))
        kotlin.test.assertNotNull(resolveSpaceBannerColorFilter(isLight = true, hasFilter = true))
        kotlin.test.assertNotNull(resolveSpaceBannerColorFilter(isLight = false, hasFilter = true))
    }

    @Test
    fun `resolveSpaceDisplayTags extracts location and preserves real_name tags`() {
        val tags = listOf(
            SpaceTagItem(type = "location", title = "IP属地：广东"),
            SpaceTagItem(type = "real_name", title = "已实名认证", uri = "https://www.bilibili.com/verify"),
            SpaceTagItem(type = "other", title = "不应展示")
        )
        val result = resolveSpaceDisplayTags(tags)

        assertEquals(2, result.size)
        assertEquals("IP属地：广东", result[0].title)
        assertEquals("location", result[0].type)
        assertEquals("已实名认证", result[1].title)
        assertEquals("real_name", result[1].type)
        assertEquals("https://www.bilibili.com/verify", result[1].uri)
    }

    @Test
    fun `resolveSpaceDisplayTags falls back to ipLocation when spaceTag has no location`() {
        val tags = listOf(
            SpaceTagItem(type = "real_name", title = "已实名认证")
        )
        val result = resolveSpaceDisplayTags(tags, ipLocation = "北京")

        assertEquals(2, result.size)
        assertEquals("IP属地：北京", result[0].title)
        assertEquals("location", result[0].type)
        assertEquals("已实名认证", result[1].title)
    }

    @Test
    fun `resolveSpaceDisplayTags puts sex after location only when enabled`() {
        val tags = listOf(
            SpaceTagItem(type = "location", title = "IP属地：广东"),
            SpaceTagItem(type = "real_name", title = "已实名认证")
        )

        val enabled = resolveSpaceDisplayTags(tags, sex = "男", showSex = true)
        assertEquals(listOf("location", "sex", "real_name"), enabled.map { it.type })
        assertEquals("男", enabled[1].title)

        val disabled = resolveSpaceDisplayTags(tags, sex = "男", showSex = false)
        assertEquals(listOf("location", "real_name"), disabled.map { it.type })

        val privacy = resolveSpaceDisplayTags(tags, sex = "保密", showSex = true)
        assertEquals("保密", privacy[1].title)

        val unknown = resolveSpaceDisplayTags(tags, sex = "未知", showSex = true)
        assertEquals(listOf("location", "real_name"), unknown.map { it.type })
    }

    @Test
    fun `resolveSpaceBannerScrollEffect interpolates scale and blur for scroll and pull down`() {
        val atRest = resolveSpaceBannerScrollEffect(
            scrollOffsetPx = 0f,
            scrollRangePx = 400f,
        )
        assertEquals(1f, atRest.scale, 0.001f)
        assertEquals(0f, atRest.blurRadiusDp, 0.001f)

        val quarter = resolveSpaceBannerScrollEffect(
            scrollOffsetPx = 100f,
            scrollRangePx = 400f,
        )
        // ease-out(0.25) = 1 - 0.75^2 = 0.4375
        assertEquals(1f + (SPACE_BANNER_MAX_SCROLL_SCALE - 1f) * 0.4375f, quarter.scale, 0.001f)
        assertEquals(SPACE_BANNER_MAX_SCROLL_BLUR_DP * 0.4375f, quarter.blurRadiusDp, 0.001f)

        val pullDown = resolveSpaceBannerScrollEffect(
            scrollOffsetPx = 0f,
            scrollRangePx = 400f,
            pullDownPx = 200f,
            pullRangePx = 200f,
            heroHeightPx = 300f,
        )
        // minFillScale = (300 + 200) / 300 = 1.6667 > SPACE_BANNER_MAX_SCROLL_SCALE
        assertEquals(500f / 300f, pullDown.scale, 0.001f)
        assertEquals(SPACE_BANNER_MAX_PULL_BLUR_DP, pullDown.blurRadiusDp, 0.001f)

        val clamped = resolveSpaceBannerScrollEffect(
            scrollOffsetPx = Float.MAX_VALUE,
            scrollRangePx = 400f,
        )
        assertEquals(SPACE_BANNER_MAX_SCROLL_SCALE, clamped.scale, 0.001f)
        assertEquals(SPACE_BANNER_MAX_SCROLL_BLUR_DP, clamped.blurRadiusDp, 0.001f)
    }

    @Test
    fun `resolveSpaceBannerScrollEffect respects scale and blur gates`() {
        val blurDisabled = resolveSpaceBannerScrollEffect(
            scrollOffsetPx = 400f,
            scrollRangePx = 400f,
            scaleEnabled = true,
            blurEnabled = false,
        )
        assertEquals(SPACE_BANNER_MAX_SCROLL_SCALE, blurDisabled.scale, 0.001f)
        assertEquals(0f, blurDisabled.blurRadiusDp, 0.001f)

        val allDisabled = resolveSpaceBannerScrollEffect(
            scrollOffsetPx = 400f,
            scrollRangePx = 400f,
            scaleEnabled = false,
            blurEnabled = false,
        )
        assertEquals(1f, allDisabled.scale, 0.001f)
        assertEquals(0f, allDisabled.blurRadiusDp, 0.001f)
    }

    @Test
    fun `resolveSpaceBannerZoomHapticStep quantizes zoom progress into discrete steps`() {
        assertEquals(0, resolveSpaceBannerZoomHapticStep(scrollOffsetPx = 0f, scrollRangePx = 400f))
        assertEquals(0, resolveSpaceBannerZoomHapticStep(scrollOffsetPx = 99f, scrollRangePx = 400f))
        assertEquals(1, resolveSpaceBannerZoomHapticStep(scrollOffsetPx = 100f, scrollRangePx = 400f))
        assertEquals(2, resolveSpaceBannerZoomHapticStep(scrollOffsetPx = 200f, scrollRangePx = 400f))
        assertEquals(3, resolveSpaceBannerZoomHapticStep(scrollOffsetPx = 300f, scrollRangePx = 400f))
        assertEquals(SPACE_BANNER_ZOOM_HAPTIC_STEPS, resolveSpaceBannerZoomHapticStep(scrollOffsetPx = 400f, scrollRangePx = 400f))
        assertEquals(SPACE_BANNER_ZOOM_HAPTIC_STEPS, resolveSpaceBannerZoomHapticStep(scrollOffsetPx = Float.MAX_VALUE, scrollRangePx = 400f))
        assertEquals(
            2,
            resolveSpaceBannerZoomHapticStep(
                scrollOffsetPx = 0f,
                scrollRangePx = 400f,
                pullDownPx = 100f,
                pullRangePx = 200f,
            ),
        )
    }
}
