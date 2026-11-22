package com.android.purebilibili.feature.list

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.util.PickGalleryVisualMedia
import com.android.purebilibili.data.model.response.FavoriteInfo

@Composable
internal fun FavoriteFolderEditorDialog(
    folder: FavoriteInfo,
    accountKey: AccountSessionIdentity,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Uri?) -> Unit,
) {
    var title by rememberSaveable(folder.id, accountKey) { mutableStateOf(folder.title) }
    var intro by rememberSaveable(folder.id, accountKey) { mutableStateOf(folder.intro) }
    var isPrivate by rememberSaveable(folder.id, accountKey) {
        mutableStateOf(isFavoriteFolderPrivate(folder.attr))
    }
    var coverUriString by rememberSaveable(folder.id, accountKey) { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isSaving) {
        if (isSaving) focusManager.clearFocus(force = true)
    }
    val coverPicker = rememberLauncherForActivityResult(PickGalleryVisualMedia()) { uri ->
        if (!isSaving && uri != null) coverUriString = uri.toString()
    }
    val fieldModifier = Modifier
        .fillMaxWidth()
        .focusProperties { canFocus = !isSaving }

    AppAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = !isSaving,
        ),
        title = { AppText("编辑信息") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
            ) {
                AppTextField(
                    value = title,
                    onValueChange = { if (!isSaving) title = it },
                    modifier = fieldModifier,
                    label = "名称",
                    placeholder = "收藏夹名称",
                )
                AppText("封面")
                val cover = coverUriString ?: folder.cover
                if (cover.isNotBlank()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = "收藏夹封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(AppShapes.mediaCover()),
                    )
                }
                AppTextButton(
                    enabled = !isSaving,
                    onClick = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                ) {
                    AppText("从相册选择封面")
                }
                AppTextField(
                    value = intro,
                    onValueChange = { if (!isSaving) intro = it },
                    modifier = fieldModifier,
                    label = "简介",
                    placeholder = "可选",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppText(if (isPrivate) "私密收藏夹" else "公开收藏夹")
                    AppSwitch(
                        checked = isPrivate,
                        onCheckedChange = { if (!isSaving) isPrivate = it },
                        enabled = !isSaving,
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                enabled = !isSaving && title.isNotBlank(),
                onClick = {
                    if (!isSaving && title.isNotBlank()) {
                        focusManager.clearFocus(force = true)
                        onSave(title, intro, isPrivate, coverUriString?.let(Uri::parse))
                    }
                },
            ) {
                AppText(if (isSaving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            AppTextButton(enabled = !isSaving, onClick = onDismiss) {
                AppText("取消")
            }
        },
    )
}
