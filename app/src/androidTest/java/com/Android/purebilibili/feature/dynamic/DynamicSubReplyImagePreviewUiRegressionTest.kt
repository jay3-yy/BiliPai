package com.Android.purebilibili.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.feature.dynamic.components.DynamicSubReplyPreviewHost
import com.android.purebilibili.feature.dynamic.components.IMAGE_PREVIEW_PAGE_TAG
import com.android.purebilibili.feature.dynamic.components.ImagePreviewOverlayHost
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicSubReplyImagePreviewUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingPictureInsideDynamicSubReply_opensImagePreview() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                ) {
                    DynamicSubReplyPreviewHost(
                        onSortModeChange = {},
                        state = SubReplyUiState(
                            visible = true,
                            rootReply = ReplyItem(
                                rpid = 1L,
                                mid = 2L,
                                ctime = 1_700_000_000L,
                                member = ReplyMember(
                                    mid = "2",
                                    uname = "DynamicTester"
                                ),
                                content = ReplyContent(
                                    message = "root with picture",
                                    pictures = listOf(
                                        ReplyPicture(
                                            imgSrc = "https://example.com/dynamic-subreply.jpg",
                                            imgWidth = 600,
                                            imgHeight = 600
                                        )
                                    )
                                )
                            ),
                            items = persistentListOf(
                                ReplyItem(
                                    rpid = 2L,
                                    mid = 3L,
                                    root = 1L,
                                    ctime = 1_700_000_030L,
                                    member = ReplyMember(
                                        mid = "3",
                                        uname = "ChildTester"
                                    ),
                                    content = ReplyContent(
                                        message = "child with picture",
                                        pictures = listOf(
                                            ReplyPicture(
                                                imgSrc = "https://example.com/dynamic-child-subreply.jpg",
                                                imgWidth = 640,
                                                imgHeight = 640
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        onDismiss = {},
                        onRefresh = {},
                        onUserClick = {},
                        onLoadMore = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule
            .onNodeWithTag("subreply_detail_image_1_0")
            .performClick()

        composeTestRule
            .onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun clickingChildPictureInsideDynamicSubReply_usesDedicatedDetailImageTag() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                ) {
                    DynamicSubReplyPreviewHost(
                        onSortModeChange = {},
                        state = SubReplyUiState(
                            visible = true,
                            rootReply = ReplyItem(
                                rpid = 1L,
                                mid = 2L,
                                ctime = 1_700_000_000L,
                                member = ReplyMember(
                                    mid = "2",
                                    uname = "DynamicTester"
                                ),
                                content = ReplyContent(
                                    message = "root with picture",
                                    pictures = listOf(
                                        ReplyPicture(
                                            imgSrc = "https://example.com/dynamic-subreply.jpg",
                                            imgWidth = 600,
                                            imgHeight = 600
                                        )
                                    )
                                )
                            ),
                            items = persistentListOf(
                                ReplyItem(
                                    rpid = 2L,
                                    mid = 3L,
                                    root = 1L,
                                    ctime = 1_700_000_030L,
                                    member = ReplyMember(
                                        mid = "3",
                                        uname = "ChildTester"
                                    ),
                                    content = ReplyContent(
                                        message = "child with picture",
                                        pictures = listOf(
                                            ReplyPicture(
                                                imgSrc = "https://example.com/dynamic-child-subreply.jpg",
                                                imgWidth = 640,
                                                imgHeight = 640
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        onDismiss = {},
                        onRefresh = {},
                        onUserClick = {},
                        onLoadMore = {}
                    )
                    ImagePreviewOverlayHost(modifier = Modifier.fillMaxSize())
                }
            }
        }

        composeTestRule
            .onNodeWithTag("subreply_detail_image_2_0")
            .performClick()

        composeTestRule
            .onNodeWithTag(IMAGE_PREVIEW_PAGE_TAG)
            .assertIsDisplayed()
    }
}
