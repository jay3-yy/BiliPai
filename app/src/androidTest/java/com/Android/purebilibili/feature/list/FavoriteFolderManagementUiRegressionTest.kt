package com.android.purebilibili.feature.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.data.model.response.FavFolder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteFolderManagementUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun overviewMenuOnlyOffersCreationAndExplicitBatchEntry() {
        rule.setContent {
            MaterialTheme {
                FavoriteFolderOverviewMenu(false, true, {}, {}) {
                    AppIcon(Icons.Filled.MoreVert, contentDescription = "更多管理")
                }
            }
        }
        rule.onNodeWithContentDescription("更多管理").performClick()
        rule.onNodeWithText("新建收藏夹").assertIsEnabled()
        rule.onNodeWithText("多选/批量管理").assertIsEnabled()
        for (label in listOf("分享至动态", "删除收藏夹", "分享收藏夹", "清理失效内容")) {
            rule.onNodeWithText(label).assertDoesNotExist()
        }
    }

    @Test fun folderSelectionRevealsActionsAndRespectsDefaultAndPrivateFolders() {
        val folders = listOf(
            FavFolder(id = 1, mid = 101, title = "默认收藏夹"),
            FavFolder(id = 2, mid = 101, title = "普通收藏"),
            FavFolder(id = 3, mid = 101, title = "私密收藏", attr = 1),
        )
        val selected = mutableStateOf(emptySet<Long>())
        val selecting = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Column {
                    if (selecting.value) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            FavoriteFolderBatchActions(
                                folders = folders,
                                selectedIds = selected.value,
                                permissions = favoriteFolderBatchPermissions(
                                    folders.filter { it.id in selected.value }, 1, 101,
                                ),
                                busy = false,
                                onSelectionChange = { selected.value = it },
                                onDelete = {}, onShare = {}, onClean = {},
                            )
                        }
                    }
                    FavoriteFolderCardList(
                        folders = folders, subscribedFoldersCount = 0, searchQuery = "",
                        padding = PaddingValues(0.dp), transitionEnabled = false,
                        onFolderClick = {}, onSubscribedClick = {},
                        selectionMode = selecting.value, selectedMediaIds = selected.value,
                        onStartSelection = { selecting.value = true; selected.value = setOf(it.id) },
                        onToggleSelection = { folder ->
                            selected.value = if (folder.id in selected.value) selected.value - folder.id else selected.value + folder.id
                        },
                    )
                }
            }
        }
        rule.onNodeWithText("删除收藏夹").assertDoesNotExist()
        rule.onNodeWithText("普通收藏").performTouchInput { longClick() }
        rule.runOnIdle { assertEquals(setOf(2L), selected.value) }
        rule.onNodeWithText("删除收藏夹").assertIsEnabled()
        rule.onNodeWithText("分享收藏夹").assertIsEnabled()
        rule.onNodeWithText("清理失效内容").assertIsEnabled()
        rule.onNodeWithText("默认收藏夹").performClick()
        rule.onNodeWithText("删除收藏夹").assertIsNotEnabled()
        rule.onNodeWithText("分享收藏夹").assertIsEnabled()
        rule.onNodeWithText("私密收藏").performClick()
        rule.onNodeWithText("分享收藏夹").assertIsNotEnabled()
        rule.onNodeWithText("清理失效内容").assertIsEnabled()
        rule.onNodeWithText("取消全选").performClick()
        rule.onNodeWithText("删除收藏夹").assertDoesNotExist()
        rule.onNodeWithText("分享收藏夹").assertDoesNotExist()
        rule.onNodeWithText("清理失效内容").assertDoesNotExist()
    }

    @Test fun detailMenuKeepsSharingAndRemovalScopedToFolderPermissions() {
        val canManage = mutableStateOf(true)
        val canShare = mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                AppWindowActionMenu(
                    groups = listOf(favoriteDetailManagementActions(
                        canCopy = true,
                        canManage = canManage.value,
                        canShare = canShare.value,
                        hasItems = true,
                        busy = false,
                        onShare = {},
                        onSelect = {},
                    )),
                ) {
                    AppIcon(Icons.Filled.MoreVert, contentDescription = "收藏夹管理")
                }
            }
        }
        rule.onNodeWithContentDescription("收藏夹管理").performClick()
        rule.onNodeWithText("分享至动态").assertIsEnabled()
        rule.onNodeWithText("批量取消收藏/移除收藏").assertIsEnabled()
        rule.runOnIdle { canShare.value = false }
        rule.onNodeWithText("分享至动态").assertIsNotEnabled()
        rule.onNodeWithText("批量取消收藏/移除收藏").assertIsEnabled()
        rule.runOnIdle { canManage.value = false }
        rule.onNodeWithText("批量取消收藏/移除收藏").assertDoesNotExist()
        rule.onNodeWithText("多选/批量复制").assertIsEnabled()
    }
}
