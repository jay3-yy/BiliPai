package com.android.purebilibili.navigation3

import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.settings.SettingsSharedContainerOrigin
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsContainerMotionPolicyTest {
    private val source = Rect(16f, 220f, 384f, 300f)
    private val page = Rect(0f, 0f, 400f, 900f)

    @Test
    fun sharedContainerInterpolatesPhysicalCornersForOpeningAndReturn() {
        val origin = SettingsSharedContainerOrigin(BiliPaiNavKey.Settings, Color.Black).apply {
            topRadius = 12.dp
            bottomRadius = 0.dp
        }
        val open = mutableFloatStateOf(0f)
        val window = WindowCornerRadii(
            topLeft = 48f, topRight = 56f, bottomRight = 40f, bottomLeft = 32f,
        )
        val shape = settingsSharedContainerShape(origin, open) { window }
        fun radii(direction: LayoutDirection): List<Float> {
            val outline = shape.createOutline(Size(400f, 500f), direction, Density(2f))
                as Outline.Rounded
            return with(outline.roundRect) {
                listOf(topLeftCornerRadius.x, topRightCornerRadius.x, bottomRightCornerRadius.x, bottomLeftCornerRadius.x)
            }
        }
        assertEquals(listOf(24f, 24f, 0f, 0f), radii(LayoutDirection.Ltr))
        open.floatValue = 0.5f
        val halfway = radii(LayoutDirection.Ltr)
        assertEquals(listOf(36f, 40f, 20f, 16f), halfway)
        open.floatValue = 1f
        assertEquals(listOf(48f, 56f, 40f, 32f), radii(LayoutDirection.Rtl))
        open.floatValue = 0.5f
        assertEquals(halfway, radii(LayoutDirection.Rtl))
    }

    @Test
    fun releasingOrRegrabbingContainerPreservesBoundsCornersAndContentAtTheSameDepth() {
        for (size in listOf(IntSize(400, 900), IntSize(1000, 800), IntSize(280, 700))) {
            val destination = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            val origin = Rect(16f, 220f, size.width - 16f, 300f)
            for (depth in listOf(-0.8f, -0.5f, -0.2f)) {
                val heldScope = containerScope(size, depth, null)
                val held = resolveSettingsContainerFrame(origin, destination, heldScope,
                    hasOpened = true, videoCardMotionEnabled = false)
                for (phase in listOf(NavSettlePhase.Cancel, NavSettlePhase.Commit)) {
                    val released = resolveSettingsContainerFrame(origin, destination, containerScope(size, depth, phase),
                        hasOpened = true, videoCardMotionEnabled = false)
                    assertEquals(held, released, "phase=$phase size=$size depth=$depth")
                }
                if (depth == -0.5f) assertEquals(0.5f, held.cornerFraction)
            }
        }
    }

    private fun containerScope(size: IntSize, depth: Float, phase: NavSettlePhase?): NavTransitionScope =
        object : NavTransitionScope {
            override val relativeDepth = depth
            override val role = if (phase == NavSettlePhase.Commit) NavRole.Outgoing else NavRole.Incoming
            // 取消不改变栈，保留最近一次 Push；手势元数据在松手收敛期间仍存在。
            override val change = if (phase == NavSettlePhase.Commit) NavChange.Pop else NavChange.Push
            override val isRemoving = phase == NavSettlePhase.Commit
            override val gesture = NavGesture(-depth, NavSwipeEdge.Left, size.height / 2f)
            override val settle: NavSettle? = phase?.let {
                object : NavSettle {
                    override val phase = it
                    override val releaseVelocity = 0f
                    override val elapsedMillis = 0f
                }
            }
            override val layoutSize = size
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(1f)
        }

    @Test
    fun mediaAndUpCardsReturnOnlyToTheirActualNavigationOwner() {
        val owner = BiliPaiNavKey.Search()
        val targets = listOf(BiliPaiNavKey.Space(42L), BiliPaiNavKey.BangumiDetail(123L, epId = 456L))
        targets.forEach { target ->
            assertTrue(isSettingsContainerBoundary(true, listOf(owner, target), target, owner))
            assertFalse(isSettingsContainerBoundary(false, listOf(owner, target), target, owner))
            assertFalse(isSettingsContainerBoundary(true, listOf(BiliPaiNavKey.Space(99L), target), target, owner))
        }
        val episode = BiliPaiNavKey.BangumiDetail(123L, epId = 456L)
        assertFalse(isSettingsContainerBoundary(true, listOf(owner, episode.copy(epId = 789L)), episode, owner))
    }

    @Test
    fun shopWebContainerRequiresItsCapturedOwnerAndExactProduct() {
        val owner = BiliPaiNavKey.Space(123L)
        val target = BiliPaiNavKey.Web("https://mall.bilibili.com/item/1", "商品")
        assertTrue(isSettingsContainerBoundary(true, listOf(owner, target), target, owner))
        assertFalse(isSettingsContainerBoundary(false, listOf(owner, target), target, owner))
        assertFalse(isSettingsContainerBoundary(true, listOf(BiliPaiNavKey.Space(456L), target), target, owner))
        assertFalse(isSettingsContainerBoundary(true, listOf(owner, target.copy(url = "https://mall.bilibili.com/item/2")), target, owner))
        assertFalse(isSettingsContainerBoundary(true, listOf(target), target, null))
    }

    @Test
    fun liveAreaIconReturnsOnlyToItsCapturedCategorySession() {
        val owner = BiliPaiNavKey.LiveArea
        val target = BiliPaiNavKey.LiveAreaDetail(2, 86, "英雄联盟", openId = 10L)
        assertTrue(isSettingsContainerBoundary(true, listOf(owner, target), target, owner))
        assertFalse(isSettingsContainerBoundary(false, listOf(owner, target), target, owner))
        assertFalse(isSettingsContainerBoundary(true, listOf(owner, target.copy(openId = 11L)), target, owner))
        assertFalse(isSettingsContainerBoundary(true, listOf(BiliPaiNavKey.LiveList, target), target, owner))
    }

    @Test
    fun navigationSpringPassesThroughLandingAndSettlesWithoutASecondImpulse() {
        val animation = TargetBasedAnimation(
            animationSpec = containerReturnSpringSpec(),
            typeConverter = Float.VectorConverter,
            initialValue = 1f,
            targetValue = 0f,
            initialVelocity = -1f,
        )
        for (origin in listOf(source, Rect(140f, 170f, 200f, 230f))) {
            var crossedEndpoint = false
            var minimumWidth = origin.width
            for (ms in 0..(animation.durationNanos / 1_000_000L).toInt()) {
                val remaining = animation.getValueFromNanos(ms * 1_000_000L)
                val frame = resolveSettingsContainerFrame(origin, page, remaining - 1f,
                    returning = true, springDriven = true)
                crossedEndpoint = crossedEndpoint || remaining < 0f
                minimumWidth = minOf(minimumWidth, frame.bounds.width)
                assertTrue(frame.bounds.width >= origin.width * 0.97f - 0.001f)
                assertTrue(frame.contentAlpha in 0f..1f)
                assertTrue(frame.cornerFraction in 0f..1f)
            }
            assertTrue(crossedEndpoint)
            assertTrue(minimumWidth < origin.width)
            val landed = resolveSettingsContainerFrame(origin, page,
                animation.getValueFromNanos(animation.durationNanos) - 1f,
                returning = true, springDriven = true)
            assertEquals(origin, landed.bounds)
        }
    }

    @Test
    fun liveCardsAndAvatarsReturnOnlyToThePageThatCapturedThem() {
        val room = BiliPaiNavKey.Live(roomId = "123", title = "直播", uname = "主播")
        assertTrue(isSettingsContainerBoundary(true, listOf(BiliPaiNavKey.MainHost, room), room, BiliPaiNavKey.MainHost))
        val followingStack = listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.LiveFollowing, room)
        assertTrue(isSettingsContainerBoundary(true, followingStack, room, BiliPaiNavKey.LiveFollowing))
        assertFalse(isSettingsContainerBoundary(true, followingStack, room, BiliPaiNavKey.MainHost))
        assertFalse(isSettingsContainerBoundary(true, followingStack, room.copy(title = "旧标题"), BiliPaiNavKey.LiveFollowing))
        assertFalse(isSettingsContainerBoundary(false, followingStack, room, BiliPaiNavKey.LiveFollowing))
    }

    @Test
    fun realtimeBlurOnlyAffectsCoveredBackgroundAndReversesWithNavigationDepth() {
        for (depth in listOf(-1f, -0.5f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0f, resolveSettingsContainerBlurProgress(depth))
        }
        assertEquals(1f, resolveSettingsContainerBlurProgress(1f))
        assertEquals(1f, resolveSettingsContainerBlurProgress(2f))
        val enter = (0..10).map { resolveSettingsContainerBlurProgress(it / 10f) }
        val leave = (10 downTo 0).map { resolveSettingsContainerBlurProgress(it / 10f) }
        assertEquals(enter.reversed(), leave)
        assertTrue(enter.zipWithNext().all { (previous, next) -> next >= previous })
    }

    @Test
    fun pushAndPopHaveExactRowAndPageEndpoints() {
        for (returning in listOf(false, true)) {
            val closed = resolveSettingsContainerFrame(source, page, -1f, returning)
            val open = resolveSettingsContainerFrame(source, page, 0f, returning)
            assertEquals(source, closed.bounds)
            assertEquals(0f, closed.contentAlpha)
            assertEquals(1f, closed.cornerFraction)
            assertEquals(page, open.bounds)
            assertEquals(1f, open.contentAlpha)
            assertEquals(0f, open.cornerFraction)
        }
    }

    @Test
    fun pushAndReturnUseDistinctContentFadeStages() {
        assertEquals(0f, resolveSettingsContainerFrame(source, page, -0.8f, false).contentAlpha, 0.0001f)
        assertEquals(0.5f, resolveSettingsContainerFrame(source, page, -0.7f, false).contentAlpha, 0.0001f)
        assertEquals(1f, resolveSettingsContainerFrame(source, page, -0.6f, false).contentAlpha, 0.0001f)
        assertEquals(0f, resolveSettingsContainerFrame(source, page, -0.4f, true).contentAlpha, 0.0001f)
        assertEquals(0.5f, resolveSettingsContainerFrame(source, page, -0.3f, true).contentAlpha, 0.0001f)
        assertEquals(1f, resolveSettingsContainerFrame(source, page, -0.2f, true).contentAlpha, 0.0001f)
    }

    @Test
    fun containerAlwaysGrowsAroundTheSourceWithoutOvershooting() {
        for (returning in listOf(false, true)) {
            var previous = source
            for (step in 0..100) {
                val frame = resolveSettingsContainerFrame(source, page, step / 100f - 1f, returning)
                assertTrue(frame.bounds.left <= previous.left + 0.001f)
                assertTrue(frame.bounds.top <= previous.top + 0.001f)
                assertTrue(frame.bounds.right >= previous.right - 0.001f)
                assertTrue(frame.bounds.bottom >= previous.bottom - 0.001f)
                assertTrue(frame.contentAlpha in 0f..1f)
                assertTrue(frame.cornerFraction in 0f..1f)
                previous = frame.bounds
            }
        }
    }

    @Test
    fun settleOvershootCannotEscapeTheRowOrPageBounds() {
        assertEquals(source, resolveSettingsContainerFrame(source, page, -1.2f, false).bounds)
        assertEquals(page, resolveSettingsContainerFrame(source, page, 0.2f, true).bounds)
    }

    @Test
    fun onlyTheCapturedParentBoundaryMayExpand() {
        val target = BiliPaiNavKey.AppearanceSettings
        val owner = BiliPaiNavKey.Settings
        val stack = listOf(BiliPaiNavKey.MainHost, owner, target)
        assertTrue(isSettingsContainerBoundary(true, stack, target, owner))
        assertFalse(isSettingsContainerBoundary(false, stack, target, owner))
        assertFalse(isSettingsContainerBoundary(true, stack, target, null))
        assertFalse(isSettingsContainerBoundary(true, listOf(owner, BiliPaiNavKey.SettingsSearch, target), target, owner))
        assertFalse(isSettingsContainerBoundary(true, listOf(target), target, owner))
        assertFalse(isSettingsContainerBoundary(true, listOf(owner), target, owner))
    }
}
