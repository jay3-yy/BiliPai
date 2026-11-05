package com.android.purebilibili.feature.video

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.video.screen.videoDetailWholeCardReturnLayer
import com.android.purebilibili.feature.video.screen.rememberVideoDetailWholeCardReturn
import com.android.purebilibili.feature.video.screen.VideoDetailReturnCoverImage
import com.android.purebilibili.feature.video.screen.resolveVideoDetailReturnMediaFrame
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.util.CardPositionManager
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class VideoDetailWholeCardReturnUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun liveDetailDrawingIsReusedDuringBlendAndRefreshesAfterCancellation() {
        val alpha = mutableFloatStateOf(0f)
        val color = mutableStateOf(Color.Red)
        val draws = AtomicInteger()
        val snapshot = solidBitmap(android.graphics.Color.GREEN)
        rule.setContent {
            Box(Modifier.size(120.dp).background(Color.Black)
                .videoDetailWholeCardReturnLayer(snapshot) { alpha.floatValue }
                .drawWithContent {
                    draws.incrementAndGet()
                    drawRect(color.value)
                }.testTag("cached-live-detail"))
        }
        fun pixel(): Color {
            val pixels = rule.onNodeWithTag("cached-live-detail").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 4]
        }
        assertTrue(pixel().red > 0.9f)
        val initialDraws = draws.get()
        for (opacity in listOf(0.2f, 0.5f, 0.8f, 1f, 0.5f, 0f)) {
            rule.runOnIdle { alpha.floatValue = opacity }
            val sample = pixel()
            assertEquals(1f - opacity, sample.red, 0.03f)
            assertEquals(opacity, sample.green, 0.03f)
            assertEquals("Blend ticks must reuse live drawing", initialDraws, draws.get())
        }
        rule.runOnIdle {
            color.value = Color.Blue
            alpha.floatValue = 0.5f
        }
        val changed = pixel()
        assertTrue(changed.blue > 0.45f && changed.green > 0.45f && changed.red < 0.05f)
        assertTrue("Real content changes must still redraw", draws.get() > initialDraws)
        rule.runOnIdle { alpha.floatValue = 0f }
        assertTrue("Cancel must show the latest live content", pixel().blue > 0.9f)
    }

    @Test
    fun directSnapshotAlphaMatchesGroupBlendWithTransparentEdgesAndOverlappingContent() {
        val alpha = mutableFloatStateOf(0f)
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.TRANSPARENT)
            for (y in 0 until 40) for (x in 0 until 20) {
                setPixel(x, y, android.graphics.Color.argb(128, 0, 255, 0))
            }
        }.asImageBitmap()
        rule.setContent {
            Row {
                for (reference in listOf(false, true)) {
                    val blend = if (!reference) {
                        Modifier.videoDetailWholeCardReturnLayer(bitmap) { alpha.floatValue }
                    } else Modifier.drawWithContent {
                        val opacity = alpha.floatValue
                        if (opacity <= 0f) {
                            drawContent()
                        } else {
                            val paint = Paint()
                            drawIntoCanvas { canvas ->
                                if (opacity < 1f) {
                                    paint.alpha = 1f - opacity
                                    canvas.saveLayer(Rect(Offset.Zero, size), paint)
                                    try { this@drawWithContent.drawContent() } finally { canvas.restore() }
                                }
                                paint.alpha = opacity
                                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                                try {
                                    scale(size.width / bitmap.width, pivot = Offset.Zero) { drawImage(bitmap) }
                                } finally { canvas.restore() }
                            }
                        }
                    }
                    Box(Modifier.size(80.dp).background(Color.Black).then(blend)
                        .drawWithContent {
                            drawRect(Color.Red)
                            drawRect(Color.Blue, topLeft = Offset(size.width / 4f, 0f))
                        }.testTag(if (reference) "group-reference" else "direct-bitmap"))
                }
            }
        }
        for (opacity in listOf(0f, 0.25f, 0.5f, 1f, 0.5f, 0f)) {
            rule.runOnIdle { alpha.floatValue = opacity }
            val actual = rule.onNodeWithTag("direct-bitmap").captureToImage().toPixelMap()
            val expected = rule.onNodeWithTag("group-reference").captureToImage().toPixelMap()
            for (fraction in listOf(0.1f, 0.35f, 0.75f)) {
                val a = actual[(actual.width * fraction).toInt(), actual.height / 2]
                val e = expected[(expected.width * fraction).toInt(), expected.height / 2]
                assertEquals("red at opacity=$opacity x=$fraction", e.red, a.red, 0.02f)
                assertEquals("green at opacity=$opacity x=$fraction", e.green, a.green, 0.02f)
                assertEquals("blue at opacity=$opacity x=$fraction", e.blue, a.blue, 0.02f)
            }
        }
    }


    @Test
    fun quickPredictiveReturnKeepsResidentCoverUntilSnapshotAndRestoresItOnCancel() {
        val depth = mutableFloatStateOf(0.9f)
        val gesture = mutableStateOf(true)
        val decoded = mutableIntStateOf(0)
        val residentCover = Bitmap.createBitmap(240, 135, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }
        val snapshot = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }.asImageBitmap()
        try {
            rule.runOnIdle {
                CardPositionManager.clear()
                recordCard("BVquick", "home")
            }
            rule.setContent {
                val context = LocalContext.current
                val request = remember(context) {
                    ImageRequest.Builder(context).data(residentCover).size(240, 135).crossfade(false).build()
                }
                val wholeCard = rememberVideoDetailWholeCardReturn(
                    bvid = "BVquick", sourceRoute = "home", ownsTransition = true,
                    committedReturn = false, keepLoadedContentForBackPreview = false,
                    sourceBounds = Rect(0f, 0f, 240f, 180f), sourceLayout = VideoCardSourceLayout.STACKED,
                    isNowPlayingBar = false, depthProvider = { depth.floatValue },
                    phaseProvider = {
                        if (gesture.value) VideoCardTransitionBackgroundPhase.RETURNING
                        else VideoCardTransitionBackgroundPhase.HELD
                    },
                    gestureProvider = { gesture.value },
                )
                // Player has no decoded frame yet; the resident cover must hide its black backing.
                Box(Modifier.size(240.dp, 240.dp).background(Color.Black)
                    .then(wholeCard.modifier).testTag("quick-return")) {
                    Column {
                        VideoDetailReturnCoverImage(
                            request = request, landingLayout = null,
                            handoffProgressProvider = { 0f }, inverseScaleXProvider = { 1f },
                            inverseScaleYProvider = { 1f }, alphaProvider = {
                                resolveVideoDetailReturnMediaFrame(
                                    transitionProgress = depth.floatValue, isCommittedCardReturn = false,
                                    hasResidentCover = true, isReturnGestureInProgress = gesture.value,
                                    showResidentCoverUntilFirstFrame = true,
                                ).coverAlpha
                            },
                            modifier = Modifier.fillMaxWidth().height(135.dp),
                            onDecoded = { decoded.intValue++ },
                        )
                        Box(Modifier.fillMaxWidth().height(45.dp).background(Color.Yellow))
                    }
                }
            }
            rule.waitUntil(timeoutMillis = 5_000) { decoded.intValue > 0 }
            fun assertPixels(snapshotWeight: Float) {
                val pixels = rule.onNodeWithTag("quick-return").captureToImage().toPixelMap()
                val cover = pixels[pixels.width / 2, (pixels.height * 50 / 240)]
                val info = pixels[pixels.width / 2, (pixels.height * 150 / 240)]
                // Both groups use source-over blending above a black player backing.
                val liveWeight = (1f - snapshotWeight) * (1f - snapshotWeight)
                assertTrue("Cover disappeared at depth=${depth.floatValue}",
                    kotlin.math.abs(cover.blue - liveWeight) < 0.04f &&
                        kotlin.math.abs(cover.green - snapshotWeight) < 0.04f && cover.red < 0.04f)
                assertTrue("Source info disappeared before takeover",
                    kotlin.math.abs(info.green - (liveWeight + snapshotWeight)) < 0.04f &&
                        kotlin.math.abs(info.red - liveWeight) < 0.04f)
            }
            assertPixels(0f) // No snapshot is ready yet.
            rule.runOnIdle {
                CardPositionManager.recordWholeCardReturnBitmap(
                    snapshot, CardPositionManager.videoCardPixelOwnerFor("home:BVquick"),
                )
            }
            // Early return, full takeover, reverse/cancel, and restart all use the same depth.
            for (progress in listOf(0.9f, 0.45f, 0.25f, 0.01f, 0.25f, 0.9f, 1f)) {
                rule.runOnIdle { depth.floatValue = progress }
                val weight = com.android.purebilibili.feature.video.screen.resolveVideoDetailWholeCardReturnAlpha(
                    progress, returning = true, committedIdle = false,
                )
                assertPixels(weight)
            }
            rule.runOnIdle { depth.floatValue = 0f }
            assertPixels(1f)
            rule.runOnIdle { gesture.value = false; depth.floatValue = 1f }
            assertPixels(0f)
        } finally {
            rule.runOnIdle {
                CardPositionManager.clear()
            }
        }
    }

    private fun recordCard(bvid: String, route: String, instanceId: Long = 1L) {
        CardPositionManager.recordVideoCardPosition(
            bvid = bvid, sourceRoute = route,
            bounds = Rect(0f, 0f, 240f, 180f),
            screenWidth = 1080f, screenHeight = 1920f,
            sourceLayout = VideoCardSourceLayout.STACKED,
            sourceInstanceId = instanceId,
        )
    }

    private fun solidBitmap(color: Int) =
        Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }.asImageBitmap()

    @Test
    fun restoringParentRevokesChildPublicationWithoutRelabellingRetainedPixels() {
        try {
            rule.runOnIdle {
                CardPositionManager.clear()
                recordCard("BVparent", "home", instanceId = 20L)
                val parent = CardPositionManager.videoCardPixelOwnerFor("home:BVparent")
                assertTrue(CardPositionManager.isActiveVideoCardPixelOwner(parent))
                recordCard("BVchild", "video/BVparent", instanceId = 21L)
                val child = CardPositionManager.videoCardPixelOwnerFor("video/BVparent:BVchild")
                assertTrue(CardPositionManager.isActiveVideoCardPixelOwner(child))
                val original = solidBitmap(android.graphics.Color.GREEN)
                CardPositionManager.recordWholeCardReturnBitmap(original, child)
                CardPositionManager.restoreVideoSource("home:BVparent", parent)
                assertEquals(false, CardPositionManager.isActiveVideoCardPixelOwner(child))
                // 恢复父元数据不能把旧父点击变成一次新的合法捕获。
                assertEquals(false, CardPositionManager.isActiveVideoCardPixelOwner(parent))
                CardPositionManager.recordWholeCardReturnBitmap(solidBitmap(android.graphics.Color.RED), child)
                CardPositionManager.recordNativeCardBitmap(solidBitmap(android.graphics.Color.RED), child)
                assertTrue(CardPositionManager.lastClickedWholeCardReturnSnapshot?.bitmap === original)
                assertTrue(CardPositionManager.lastClickedWholeCardReturnSnapshot?.owner === child)
                assertTrue(CardPositionManager.lastClickedNativeCardBitmap == null)
                // 再次点击相同来源会创建新身份，只有新身份可以发布。
                recordCard("BVparent", "home", instanceId = 20L)
                val next = CardPositionManager.videoCardPixelOwnerFor("home:BVparent")
                assertTrue(next !== parent)
                assertTrue(CardPositionManager.isActiveVideoCardPixelOwner(next))
                assertEquals(false, CardPositionManager.isActiveVideoCardPixelOwner(parent))
            }
        } finally {
            rule.runOnIdle { CardPositionManager.clear() }
        }
    }

    @Test
    fun nestedReturnCannotRelabelChildPixelsOrOverwriteParentRetainedPixels() {
        val childVisible = mutableStateOf(false)
        val redraw = mutableIntStateOf(0)
        val parentPixels = solidBitmap(android.graphics.Color.RED)
        val childPixels = solidBitmap(android.graphics.Color.GREEN)
        var parentOwner: com.android.purebilibili.core.util.VideoCardPixelOwner? = null
        try {
            rule.runOnIdle {
                recordCard("BV_A", "home")
                parentOwner = CardPositionManager.videoCardPixelOwnerFor("home:BV_A")
                CardPositionManager.recordWholeCardReturnBitmap(
                    parentPixels, CardPositionManager.videoCardPixelOwnerFor("home:BV_A"),
                )
            }
            rule.setContent {
                redraw.intValue
                Column {
                    val parent = rememberVideoDetailWholeCardReturn(
                        bvid = "BV_A", sourceRoute = "home", ownsTransition = !childVisible.value,
                        committedReturn = false, keepLoadedContentForBackPreview = false,
                        sourceBounds = Rect(0f, 0f, 240f, 180f),
                        sourceLayout = VideoCardSourceLayout.STACKED, isNowPlayingBar = false,
                        depthProvider = { 0f },
                        phaseProvider = { VideoCardTransitionBackgroundPhase.RETURNING },
                        gestureProvider = { false },
                    )
                    Box(Modifier.size(120.dp, 90.dp).then(parent.modifier)
                        .background(Color.Blue).testTag("parent-return"))
                    if (childVisible.value) key("child-entry") {
                        val child = rememberVideoDetailWholeCardReturn(
                            bvid = "BV_B", sourceRoute = "video/BV_A", ownsTransition = true,
                            committedReturn = false, keepLoadedContentForBackPreview = false,
                            sourceBounds = Rect(0f, 0f, 240f, 180f),
                            sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE, isNowPlayingBar = false,
                            depthProvider = { 0f },
                            phaseProvider = { VideoCardTransitionBackgroundPhase.RETURNING },
                            gestureProvider = { false },
                        )
                        Box(Modifier.size(120.dp, 90.dp).then(child.modifier)
                            .background(Color.Blue).testTag("child-return"))
                    }
                }
            }
            fun assertColor(tag: String, color: Color) {
                val pixels = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
                val actual = pixels[pixels.width / 2, pixels.height / 2]
                assertTrue("$tag used another entry's pixels: $actual", actual == color)
            }
            assertColor("parent-return", Color.Red)
            rule.runOnIdle {
                recordCard("BV_B", "video/BV_A", instanceId = 2L)
                CardPositionManager.recordWholeCardReturnBitmap(
                    childPixels, CardPositionManager.videoCardPixelOwnerFor("video/BV_A:BV_B"),
                )
                childVisible.value = true
            }
            assertColor("child-return", Color.Green)
            rule.runOnIdle {
                CardPositionManager.restoreVideoSource("home:BV_A", sourceOwner = parentOwner)
                childVisible.value = false
            }
            assertColor("parent-return", Color.Red)
            // Dispose the global child resource after the parent has had a chance to retain it.
            // A key-only fix would now keep showing the polluted green retained snapshot.
            rule.runOnIdle {
                CardPositionManager.clearNativeVideoCardLayers()
                redraw.intValue++
            }
            assertColor("parent-return", Color.Red)
        } finally {
            rule.runOnIdle { CardPositionManager.clear() }
        }
    }

    @Test
    fun repeatedSameKeyClickRejectsLatePixelsFromPreviousSourceInstance() {
        try {
            rule.runOnIdle {
                recordCard("BVsame", "home", instanceId = 10L)
                val first = CardPositionManager.videoCardPixelOwnerFor("home:BVsame")
                recordCard("BVsame", "home", instanceId = 11L)
                val second = CardPositionManager.videoCardPixelOwnerFor("home:BVsame")
                CardPositionManager.recordWholeCardReturnBitmap(solidBitmap(android.graphics.Color.GREEN), second)
                CardPositionManager.recordWholeCardReturnBitmap(solidBitmap(android.graphics.Color.RED), first)
                assertTrue(CardPositionManager.lastClickedWholeCardReturnSnapshot?.owner === second)
                val pixels = CardPositionManager.lastClickedWholeCardReturnSnapshot!!.bitmap.toPixelMap()
                assertTrue(pixels[120, 90] == Color.Green)
                // A second click of even the very same source instance is a different capture.
                recordCard("BVsame", "home", instanceId = 11L)
                CardPositionManager.recordWholeCardReturnBitmap(solidBitmap(android.graphics.Color.RED), second)
                assertTrue(CardPositionManager.lastClickedWholeCardReturnSnapshot == null)
                val third = CardPositionManager.videoCardPixelOwnerFor("home:BVsame")
                CardPositionManager.recordNativeCardBitmap(solidBitmap(android.graphics.Color.GREEN), third)
                assertTrue(CardPositionManager.lastClickedNativeCardBitmap != null)
                CardPositionManager.restoreVideoSource("home:BVsame", sourceOwner = second)
                assertTrue(CardPositionManager.lastClickedNativeCardBitmap == null)
            }
        } finally {
            rule.runOnIdle { CardPositionManager.clear() }
        }
    }

    @Test
    fun sameBvidFromDifferentSourceKeepsItsOriginalPixelProvenanceAfterKeyRestore() {
        try {
            rule.runOnIdle {
                recordCard("BVsame", "home", instanceId = 10L)
                val homeOwner = CardPositionManager.videoCardPixelOwnerFor("home:BVsame")
                recordCard("BVsame", "space/123", instanceId = 11L)
                val spaceOwner = CardPositionManager.videoCardPixelOwnerFor("space/123:BVsame")
                CardPositionManager.recordWholeCardReturnBitmap(
                    solidBitmap(android.graphics.Color.GREEN), spaceOwner,
                )
                CardPositionManager.recordNativeCardBitmap(
                    solidBitmap(android.graphics.Color.GREEN), spaceOwner,
                )
                assertTrue(CardPositionManager.lastClickedNativeCardBitmap != null)
                CardPositionManager.restoreVideoSource("home:BVsame", sourceOwner = homeOwner)
                assertTrue(CardPositionManager.lastClickedNativeCardBitmap == null)
                assertTrue(CardPositionManager.videoCardPixelOwnerFor("home:BVsame") == null)
                assertTrue(CardPositionManager.lastClickedWholeCardReturnSnapshot?.owner === spaceOwner)
                CardPositionManager.recordWholeCardReturnBitmap(
                    solidBitmap(android.graphics.Color.RED), homeOwner,
                )
                val captured = CardPositionManager.lastClickedWholeCardReturnSnapshot!!
                assertTrue(captured.owner.sourceKey == "space/123:BVsame")
                assertTrue(captured.bitmap.toPixelMap()[120, 90] == Color.Green)
            }
        } finally {
            rule.runOnIdle { CardPositionManager.clear() }
        }
    }

    @Test
    fun nativeCoverBadgeAndInfoKeepOneGeometryThroughReturnAndCancellation() {
        val ratio = mutableFloatStateOf(4f / 3f)
        val alpha = mutableFloatStateOf(0f)
        val frozen = mutableStateOf<ImageBitmap?>(null)
        val coverColor = mutableStateOf(Color.Blue)
        val infoColor = mutableStateOf(Color.Yellow)
        lateinit var freeze: () -> Unit
        rule.setContent {
            val layer = rememberGraphicsLayer()
            val scope = rememberCoroutineScope()
            SideEffect {
                freeze = { scope.launch { frozen.value = layer.toImageBitmap() }; Unit }
            }
            Column {
                // Record the complete stationary card, including its cover badge and info plate.
                Column(Modifier.size(120.dp, (120f / ratio.floatValue + 30f).dp).drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }) {
                    Box(Modifier.fillMaxWidth().height((120f / ratio.floatValue).dp).background(coverColor.value)) {
                        Box(Modifier.align(Alignment.TopEnd).size(12.dp).background(Color.Green))
                    }
                    Box(Modifier.fillMaxWidth().height(30.dp).background(infoColor.value))
                }
                Box(Modifier.size(240.dp, 280.dp).background(Color.Magenta)
                    .videoDetailWholeCardReturnLayer(bitmap = frozen.value) { alpha.floatValue }.testTag("native-return")) {
                    Box(Modifier.fillMaxSize().background(Color.Red))
                }
            }
        }
        for (cardRatio in listOf(4f / 3f, 16f / 10f, 16f / 9f)) {
            rule.runOnIdle {
                ratio.floatValue = cardRatio
                coverColor.value = Color.Blue
                infoColor.value = Color.Yellow
                frozen.value = null
            }
            rule.runOnIdle { freeze() }
            rule.waitUntil(timeoutMillis = 5_000) { frozen.value != null }
            // A later source redraw must not mutate the already captured return pixels.
            rule.runOnIdle { coverColor.value = Color.Cyan; infoColor.value = Color.Gray }
            for (opacity in listOf(1f, 0.5f, 0f, 0.5f, 1f)) {
                rule.runOnIdle { alpha.floatValue = opacity }
                val pixels = rule.onNodeWithTag("native-return").captureToImage().toPixelMap()
                val density = pixels.width / 240f
                fun pixel(x: Float, y: Float) = pixels[(x * density).toInt(), (y * density).toInt()]
                if (opacity == 1f) {
                    val coverBottom = 240f / cardRatio
                    val cover = pixel(120f, coverBottom - 4f)
                    val info = pixel(120f, coverBottom + 20f)
                    val badge = pixel(230f, 10f)
                    val below = pixel(120f, coverBottom + 70f)
                    assertTrue("Cover cropped or changed at ratio=$cardRatio",
                        cover.blue > 0.9f && cover.red < 0.1f && cover.green < 0.1f)
                    assertTrue("Info detached at ratio=$cardRatio", info.red > 0.9f && info.green > 0.9f)
                    assertTrue("Badge did not follow card", badge.green > 0.9f && badge.blue < 0.1f)
                    assertTrue("Detail leaked below card", below.red > 0.9f && below.blue > 0.9f)
                } else if (opacity == 0f) {
                    val restored = pixel(120f, 50f)
                    assertTrue("Cancelled return did not restore detail", restored.red > 0.9f && restored.blue < 0.1f)
                }
            }
        }
    }
}
