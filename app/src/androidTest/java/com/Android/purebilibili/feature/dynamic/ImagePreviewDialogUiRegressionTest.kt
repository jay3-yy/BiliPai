package com.Android.purebilibili.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.android.purebilibili.core.ui.components.appElasticPress
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.dynamic.components.IMAGE_PREVIEW_COMMENT_PANEL_TAG
import com.android.purebilibili.feature.dynamic.components.IMAGE_PREVIEW_PAGE_TAG
import com.android.purebilibili.feature.dynamic.components.IMAGE_PREVIEW_PAGE_INDICATOR_TAG
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewCommentContext
import com.android.purebilibili.feature.dynamic.components.ImagePreviewOverlayHost
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.background
import coil3.asImage
import coil3.imageLoader

@RunWith(AndroidJUnit4::class)
class ImagePreviewDialogUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cachedThumbnailExpandsToFullscreenWithoutWaitingForDrawHandoff() {
        assertPreviewExpands(seedThumbnail = true)
    }

    @Test
    fun coldDecodedImageExpandsAfterItsDimensionsAreReported() {
        assertPreviewExpands(seedThumbnail = false)
    }

    private fun assertPreviewExpands(seedThumbnail: Boolean) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.BLUE) }
        val file = java.io.File.createTempFile("preview-expand-", ".png", context.cacheDir)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val source = android.net.Uri.fromFile(file).toString()
        val cacheKey = coil3.memory.MemoryCache.Key(source)
        if (seedThumbnail) context.imageLoader.memoryCache?.set(cacheKey, coil3.memory.MemoryCache.Value(bitmap.asImage()))
        val sourceRect = Rect(24f, 80f, 124f, 180f)
        try {
            composeTestRule.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Magenta)) {
                        ImagePreviewDialog(
                            images = listOf(source), initialIndex = 0,
                            sourceRect = sourceRect, sourceKey = source, onDismiss = {},
                        )
                        ImagePreviewOverlayHost()
                    }
                }
            }
            composeTestRule.waitUntil(5_000L) {
                composeTestRule.onAllNodesWithTag(
                    com.android.purebilibili.feature.dynamic.components.ZOOMABLE_IMAGE_TAG,
                ).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.width?.let { it > sourceRect.width * 2f } == true
            }
            composeTestRule.mainClock.advanceTimeBy(600L)
            composeTestRule.waitForIdle()
            val backdrop = composeTestRule.onNodeWithTag(
                com.android.purebilibili.feature.dynamic.components.IMAGE_PREVIEW_BACKDROP_TAG,
            ).captureToImage().toPixelMap()
            // 展开确实跑到终点；不能仅靠子层的反向缩放令语义尺寸看似变大。
            org.junit.Assert.assertTrue(backdrop[0, 0].alpha > 0.99f)
        } finally {
            context.imageLoader.memoryCache?.remove(cacheKey)
            file.delete()
        }
    }

    @Test
    fun preparingAndRegisteringPreviewDoesNotHideTheSourceBeforeItDraws() {
        val open = mutableStateOf(false)
        val source = "https://example.com/first-frame.jpg"
        val bounds = Rect(20f, 40f, 180f, 200f)
        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.material3.Text(
                    if (com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden(bounds, source))
                        "来源已隐藏" else "来源仍可见",
                )
                if (open.value) ImagePreviewDialog(
                    images = listOf(source), initialIndex = 0, sourceRect = bounds, sourceKey = source,
                    onDismiss = { open.value = false },
                )
                // 此用例尚未创建预览窗口，验证准备和登记本身不会抢先隐藏来源。
            }
        }
        composeTestRule.runOnIdle {
            com.android.purebilibili.feature.dynamic.components.prepareImagePreviewSourceTransition(bounds, source)
        }
        composeTestRule.onNodeWithText("来源仍可见").assertIsDisplayed()
        composeTestRule.runOnIdle { open.value = true }
        composeTestRule.onNodeWithText("来源仍可见").assertIsDisplayed()
        composeTestRule.runOnIdle { open.value = false }
        composeTestRule.onNodeWithText("来源仍可见").assertIsDisplayed()
    }

    @Test
    fun swipeUpWithElasticFeedbackAncestor_dismissesPreviewOnce() {
        val dismissCount = AtomicInteger(0)
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().appElasticPress()) {
                    ImagePreviewDialog(
                        images = listOf(""),
                        initialIndex = 0,
                        onDismiss = { dismissCount.incrementAndGet() },
                    )
                    ImagePreviewOverlayHost()
                }
            }
        }
        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG)
            .performTouchInput { swipeUp() }
        composeTestRule.waitUntil(timeoutMillis = 3_000) { dismissCount.get() > 0 }
        composeTestRule.runOnIdle { assertEquals(1, dismissCount.get()) }
    }

    @Test
    fun replacingPreviewRequest_startsAtNewInitialPage() {
        val replaceRequest = mutableStateOf(false)
        // Distinct pager keys with empty image payloads: no network or decoding dependency.
        val images = listOf("", " ", "  ")
        composeTestRule.setContent {
            MaterialTheme {
                ImagePreviewDialog(
                    images = if (replaceRequest.value) images.take(2) else images,
                    initialIndex = if (replaceRequest.value) 0 else 2,
                    onDismiss = {},
                )
                ImagePreviewOverlayHost()
            }
        }
        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_INDICATOR_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("3 / 3").assertIsDisplayed()
        composeTestRule.runOnIdle { replaceRequest.value = true }
        composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()
    }

    @Test
    fun doubleTapOnPreviewImage_keepsPreviewOpen() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                ) {
                    ImagePreviewDialog(
                        images = listOf("https://example.com/demo.jpg"),
                        initialIndex = 0,
                        sourceRect = Rect(24f, 60f, 196f, 232f),
                        onDismiss = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG)
            .performTouchInput {
                doubleClick(center)
            }

        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG).assertIsDisplayed()
    }

    @Test
    fun longPressOnPreviewImage_triggersSaveActionWithoutClosingPreview() {
        var savedUrl: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                ) {
                    ImagePreviewDialog(
                        images = listOf("https://example.com/demo.jpg"),
                        initialIndex = 0,
                        sourceRect = Rect(24f, 60f, 196f, 232f),
                        onImageLongPress = { savedUrl = it },
                        onDismiss = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule
            .onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG)
            .performTouchInput {
                longClick(center)
            }

        composeTestRule.runOnIdle {
            assertEquals("https://example.com/demo.jpg", savedUrl)
        }
        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG).assertIsDisplayed()
    }

    @Test
    fun longPressOnPreviewImage_showsNativeActionDialog() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 390.dp, height = 844.dp)) {
                    ImagePreviewDialog(
                        images = listOf(
                            "https://example.com/one.jpg",
                            "https://example.com/two.jpg"
                        ),
                        initialIndex = 0,
                        onDismiss = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG).performTouchInput {
            longClick(center)
        }

        composeTestRule.onNodeWithText("分享").assertIsDisplayed()
        composeTestRule.onNodeWithText("复制链接").assertIsDisplayed()
        composeTestRule.onNodeWithText("保存图片").assertIsDisplayed()
        composeTestRule.onNodeWithText("保存全部图片").assertIsDisplayed()
    }

    @Test
    fun commentPreview_usesTheSameGalleryChromeAsOrdinaryImages() {
        var liked = false
        var replied = false

        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                ) {
                    ImagePreviewDialog(
                        images = listOf("https://example.com/comment.jpg"),
                        initialIndex = 0,
                        sourceRect = Rect(24f, 60f, 196f, 232f),
                        textContent = ImagePreviewTextContent(
                            headline = "江南大学的 Simple 本人",
                            body = "哈哈哈",
                            commentContext = ImagePreviewCommentContext(
                                authorName = "江南大学的 Simple 本人",
                                avatarUrl = "https://example.com/avatar.jpg",
                                timeText = "4小时前",
                                body = "哈哈哈",
                                originalSizeLabels = listOf("查看原图 (1.1M)"),
                                likeCount = 34,
                                liked = false,
                                onLikeClick = { liked = true },
                                onReplyClick = { replied = true }
                            )
                        ),
                        onDismiss = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule.onAllNodesWithTag(IMAGE_PREVIEW_COMMENT_PANEL_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_INDICATOR_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(false, liked)
            assertEquals(false, replied)
        }
        composeTestRule.onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("image_preview_also_post").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("image_preview_dislike").assertCountEquals(0)
    }
}
