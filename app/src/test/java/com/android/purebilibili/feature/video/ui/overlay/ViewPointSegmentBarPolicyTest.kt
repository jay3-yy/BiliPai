// 文件路径: test/.../ViewPointSegmentBarPolicyTest.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.data.model.response.ViewPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewPointSegmentBarPolicyTest {

    private fun point(from: Int, to: Int, content: String = "章节") =
        ViewPoint(from = from, to = to, content = content)

    @Test
    fun fill_tracksProgressBoundariesAndBackwardSeek() {
        val segment = NormalizedViewPointSegment(10_000, 30_000, "当前章节")
        for ((position, expected) in listOf(
            0L to 0f,
            10_000L to 0f,
            15_000L to 0.25f,
            20_000L to 0.5f,
            30_000L to 1f,
            40_000L to 1f,
            15_000L to 0.25f,
        )) {
            assertEquals(expected, viewPointSegmentFillFraction(segment, position))
        }
    }

    @Test
    fun fill_keepsCompletedAndFutureChaptersDistinctAcrossGap() {
        val first = NormalizedViewPointSegment(0, 10_000, "第一章")
        val second = NormalizedViewPointSegment(20_000, 40_000, "第二章")
        assertEquals(1f, viewPointSegmentFillFraction(first, 15_000))
        assertEquals(0f, viewPointSegmentFillFraction(second, 15_000))
        assertEquals(1f, viewPointSegmentFillFraction(first, 30_000))
        assertEquals(0.5f, viewPointSegmentFillFraction(second, 30_000))
    }

    @Test
    fun normalize_sorts_clampsAndDropsInvalidSegments() {
        val segments = normalizeViewPointSegments(
            viewPoints = listOf(
                point(from = 60, to = 30, content = "倒序"),   // 非法：to <= from
                point(from = 40, to = 120, content = "B"),
                point(from = 0, to = 50, content = "A"),
                point(from = 100, to = 200, content = "重叠段"),
                point(from = 300, to = 400, content = "  ")    // 非法：空标题
            ),
            durationMs = 250_000L
        )

        assertEquals(2, segments.size)
        assertEquals(0L to 50_000L, segments[0].fromMs to segments[0].toMs)
        // 40s 起点被前段截断到 50s；重叠部分被吸收
        assertEquals(50_000L to 200_000L, segments[1].fromMs to segments[1].toMs)
    }

    @Test
    fun normalize_clampsBeyondDuration() {
        val segments = normalizeViewPointSegments(
            viewPoints = listOf(point(from = 0, to = 9999, content = "超长段")),
            durationMs = 120_000L
        )

        assertEquals(1, segments.size)
        assertEquals(120_000L, segments[0].toMs)
    }

    @Test
    fun normalize_returnsEmptyWithoutDuration() {
        assertTrue(normalizeViewPointSegments(listOf(point(0, 10)), durationMs = 0L).isEmpty())
    }

    @Test
    fun find_locatesContainingSegment() {
        val segments = normalizeViewPointSegments(
            viewPoints = listOf(point(0, 60, "A"), point(60, 120, "B"), point(120, 180, "C")),
            durationMs = 180_000L
        )

        assertEquals("A", findViewPointSegmentAt(segments, 30_000L)?.content)
        assertEquals("B", findViewPointSegmentAt(segments, 90_000L)?.content)
    }

    @Test
    fun find_lastSegmentExtendsToVideoEnd() {
        val segments = normalizeViewPointSegments(
            viewPoints = listOf(point(0, 60, "A"), point(60, 120, "B")),
            durationMs = 180_000L
        )

        // 120s 之后视频还在继续：视为仍在最后一段，点击回跳段起点
        assertEquals("B", findViewPointSegmentAt(segments, 170_000L)?.content)
    }

    @Test
    fun find_emptySegmentsReturnsNull() {
        assertTrue(findViewPointSegmentAt(emptyList(), 0L) == null)
    }

    @Test
    fun floorShortChips_enabledWhenFewShortSegments() {
        // 600px 宽,只有一个 1s 短章节:保底 44px 远低于 60% 上限。
        val segments = listOf(
            NormalizedViewPointSegment(fromMs = 0, toMs = 1000, content = "开场"),
            NormalizedViewPointSegment(fromMs = 1000, toMs = 60_000, content = "长章节"),
        )
        assertTrue(
            shouldFloorShortViewPointChips(
                segments = segments,
                durationMs = 60_000L,
                barWidthPx = 600f,
                minChipWidthPx = 44f,
            )
        )
    }

    @Test
    fun floorShortChips_disabledWhenSegmentsTooFragmented() {
        // 20 个 1s 短章节:保底合计 880px 超过 600px 的 60%,放弃保底。
        val fragmented = (0 until 20).map {
            NormalizedViewPointSegment(fromMs = it * 1000L, toMs = (it + 1) * 1000L, content = "章节")
        } + NormalizedViewPointSegment(fromMs = 20_000L, toMs = 60_000L, content = "长章节")
        assertTrue(
            !shouldFloorShortViewPointChips(
                segments = fragmented,
                durationMs = 60_000L,
                barWidthPx = 600f,
                minChipWidthPx = 44f,
            )
        )
    }

    @Test
    fun floorShortChips_disabledOnInvalidGeometry() {
        assertTrue(
            !shouldFloorShortViewPointChips(
                segments = listOf(NormalizedViewPointSegment(0, 1000, "章节")),
                durationMs = 0L,
                barWidthPx = 600f,
                minChipWidthPx = 44f,
            )
        )
        assertTrue(
            !shouldFloorShortViewPointChips(
                segments = listOf(NormalizedViewPointSegment(0, 1000, "章节")),
                durationMs = 60_000L,
                barWidthPx = 0f,
                minChipWidthPx = 44f,
            )
        )
    }
}
