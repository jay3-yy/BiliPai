package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.AppGlassMenuSource
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appActionWithFeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppModalPresentation
import android.content.res.Configuration
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.feature.video.policy.resolveFavoriteFolderMediaId
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppTextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteFolderSheet(
    folders: List<FavFolder>,
    isLoading: Boolean,
    selectedFolderIds: Set<Long>,
    isSaving: Boolean,
    onFolderToggle: (FavFolder) -> Unit,
    onSaveClick: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    source: AppGlassMenuSource? = LocalFavoriteFolderMenuAnchor.current?.source,
    onCreateFolder: (String, String, Boolean) -> Unit = { _, _, _ -> }
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredSource = source
    var showCreateDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val sidePanel = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= 600
    val maxSheetHeight = (configuration.screenHeightDp.dp * 0.72f)
        .coerceAtLeast(360.dp)
        .coerceAtMost(configuration.screenHeightDp.dp)

    if (showCreateDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, intro, isPrivate ->
                onCreateFolder(title, intro, isPrivate)
                showCreateDialog = false
            }
        )
    }

    AppModalBottomSheet(
        modifier = modifier,
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        containerColor = MaterialTheme.colorScheme.surface,
        presentationOverride = if (sidePanel) AppModalPresentation.SideSheet else null,
        dragHandle = if (sidePanel) null else ({ BottomSheetDefaults.DragHandle() }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (sidePanel) Modifier.fillMaxHeight() else Modifier.heightIn(max = maxSheetHeight)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = if (sidePanel) 12.dp else 2.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "添加到收藏夹",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!sidePanel) {
                        Spacer(modifier = Modifier.height(2.dp))
                        AppText(
                            text = "可选择多个收藏夹",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                }
                AppTextButton(
                    onClick = { showCreateDialog = true },
                    enabled = !isSaving,
                ) { AppText("新建") }
                if (sidePanel) {
                    AppTextButton(onClick = onDismissRequest, enabled = !isSaving) { AppText("关闭") }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (sidePanel) Modifier.weight(1f) else Modifier.height(140.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AdaptiveLoadingIndicator()
                }
            } else if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (sidePanel) Modifier.weight(1f) else Modifier.height(140.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText("暂无收藏夹", color = AppContentColors.secondaryText())
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = sidePanel),
                ) {
                    items(folders, key = { resolveFavoriteFolderMediaId(it) }) { folder ->
                        FavoriteFolderItem(
                            folder = folder,
                            selected = selectedFolderIds.contains(resolveFavoriteFolderMediaId(folder)),
                            onClick = { onFolderToggle(folder) },
                        )
                    }
                }
            }

            AppHorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = "已选择 ${selectedFolderIds.size} 个收藏夹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    onClick = onSaveClick,
                    enabled = !isLoading && !isSaving,
                ) {
                    if (isSaving) {
                        AppCircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AppContentColors.onAccent(),
                        )
                    } else {
                        AppText("保存")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("新建收藏夹") },
        text = {
            Column {
                AppOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { AppText("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppOutlinedTextField(
                    value = intro,
                    onValueChange = { intro = it },
                    label = { AppText("简介 (选填)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppCheckbox(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it }
                    )
                    AppText("设为私密")
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, intro, isPrivate)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                AppText("创建")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("取消")
            }
        }
    )
}

@Composable
fun FavoriteFolderItem(
    folder: FavFolder,
    selected: Boolean,
    onClick: () -> Unit
) {
    val toggleWithHaptic = appActionWithFeedback(AppHapticScene.VIDEO_ACTIONS, onClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else Color.Transparent,
            )
            .clickable(onClick = toggleWithHaptic)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = folder.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = AppContentColors.primaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            AppText(
                text = "${folder.media_count}个内容",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText()
            )
        }
        AppCheckbox(
            checked = selected,
            onCheckedChange = { toggleWithHaptic() }
        )
    }
}
