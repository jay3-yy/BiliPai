package com.Android.purebilibili.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.imageLoader
import com.android.purebilibili.feature.dynamic.components.ZOOMABLE_IMAGE_TAG
import com.android.purebilibili.feature.dynamic.components.ZoomableImage
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import coil3.asImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap

@RunWith(AndroidJUnit4::class)
class ZoomableImageUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun decodedSourceProvidesPixelsAndFitGeometryWhileTheLargeImageIsStillLoading() {
        val pending = kotlinx.coroutines.CompletableDeferred<Unit>()
        val drawn = java.util.concurrent.atomic.AtomicInteger()
        var viewport = IntSize.Zero
        var reported: Rect? = null
        composeTestRule.setContent {
            val context = LocalContext.current
            val thumbnail = androidx.compose.runtime.remember {
                android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(android.graphics.Color.RED) }.asImage()
            }
            val loader = androidx.compose.runtime.remember {
                ImageLoader.Builder(context).components {
                    add(object : coil3.intercept.Interceptor {
                        override suspend fun intercept(chain: coil3.intercept.Interceptor.Chain): coil3.request.ImageResult {
                            pending.await()
                            return chain.proceed()
                        }
                    })
                }.build()
            }
            androidx.compose.runtime.DisposableEffect(loader) {
                onDispose { loader.shutdown() }
            }
            MaterialTheme {
                Box(Modifier.size(300.dp).onSizeChanged { viewport = it }) {
                    ZoomableImage(
                        model = android.R.drawable.ic_menu_gallery,
                        imageLoader = loader,
                        initialImage = thumbnail,
                        onDisplayRectChange = { reported = it },
                        onImageDrawn = { drawn.incrementAndGet() },
                    )
                }
            }
        }
        composeTestRule.waitUntil(5_000L) { reported != null && drawn.get() > 0 }
        composeTestRule.runOnIdle {
            assertEquals(false, pending.isCompleted)
            assertEquals(viewport.width.toFloat(), reported!!.width, 0.01f)
            assertEquals(viewport.width * 90f / 160f, reported!!.height, 0.01f)
        }
        val pixels = composeTestRule.onNodeWithTag(ZOOMABLE_IMAGE_TAG).captureToImage().toPixelMap()
        assertEquals(androidx.compose.ui.graphics.Color.Red, pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun parentHeroTransform_doesNotChangeReportedImageGeometry() {
        val transformed = mutableStateOf(true)
        var viewportSize = IntSize.Zero
        var reportedRect: Rect? = null
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    Modifier.size(300.dp)
                        .onSizeChanged { viewportSize = it }
                        .graphicsLayer {
                            scaleX = if (transformed.value) 0.55f else 1f
                            scaleY = if (transformed.value) 0.65f else 1f
                            translationX = if (transformed.value) 80f else 0f
                            translationY = if (transformed.value) -140f else 0f
                        }
                ) {
                    ZoomableImage(
                        model = android.R.drawable.ic_menu_gallery,
                        imageLoader = LocalContext.current.imageLoader,
                        onDisplayRectChange = { reportedRect = it },
                    )
                }
            }
        }
        composeTestRule.waitUntil(5_000L) { reportedRect != null }
        var transformedRect: Rect? = null
        composeTestRule.runOnIdle {
            transformedRect = reportedRect
            assertEquals(viewportSize.width / 2f, reportedRect!!.center.x, 0.001f)
            assertEquals(viewportSize.height / 2f, reportedRect!!.center.y, 0.001f)
            transformed.value = false
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { assertEquals(transformedRect, reportedRect) }
    }

    @Test
    fun tapAfterRecomposition_usesLatestCallbackWithoutRestartingGestureHandler() {
        val currentAction = mutableStateOf(0)
        val actions = mutableListOf<Int>()
        composeTestRule.setContent {
            val action = currentAction.value
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    ZoomableImage(
                        model = android.R.drawable.ic_menu_gallery,
                        imageLoader = LocalContext.current.imageLoader,
                        onClick = { actions.add(action) },
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag(ZOOMABLE_IMAGE_TAG).performTouchInput { click(center) }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { currentAction.value = 1 }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ZOOMABLE_IMAGE_TAG).performTouchInput {
            advanceEventTime(500L)
            click(center)
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { assertEquals(listOf(0, 1), actions) }
    }

    @Test
    fun previewImage_usesContainerBoundsInsteadOfIntrinsicThumbnailSize() {
        lateinit var imageLoader: ImageLoader

        composeTestRule.setContent {
            MaterialTheme {
                imageLoader = LocalContext.current.imageLoader
                Box(
                    modifier = Modifier.size(width = 390.dp, height = 844.dp)
                ) {
                    ZoomableImage(
                        model = android.R.drawable.ic_menu_gallery,
                        imageLoader = imageLoader
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(ZOOMABLE_IMAGE_TAG).assertIsDisplayed()

        val bounds = composeTestRule
            .onNodeWithTag(ZOOMABLE_IMAGE_TAG)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(bounds.width > 300f)
        assertTrue(bounds.height > 700f)
    }
}
