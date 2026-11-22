package com.android.purebilibili.feature.dynamic.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.data.model.response.DynamicCreatedVote
import com.android.purebilibili.data.repository.DynamicCreateRepository
import com.android.purebilibili.data.repository.DynamicVoteRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicCreateVoteDialog(
    voteId: Long? = null,
    onDismiss: () -> Unit,
    onCreated: (DynamicCreatedVote) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var imageUrls by remember { mutableStateOf(listOf("", "")) }
    var imageVote by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(voteId != null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var uploadIndex by remember { mutableIntStateOf(-1) }
    var choiceCount by remember { mutableIntStateOf(1) }
    var endMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val index = uploadIndex
        if (uri != null && index in imageUrls.indices) {
            loading = true
            scope.launch {
                DynamicCreateRepository.uploadVoteImage(context, uri).fold(
                    onSuccess = { url -> imageUrls = imageUrls.toMutableList().also { it[index] = url } },
                    onFailure = { errorMessage = it.message ?: "图片上传失败" },
                )
                loading = false
            }
        }
    }
    LaunchedEffect(voteId, loadAttempt) {
        if (voteId != null) {
            loading = true
            loadFailed = false
            errorMessage = null
            DynamicVoteRepository.getVoteInfo(voteId).fold(
                onSuccess = { info ->
                    title = info.title
                    description = info.desc
                    options = info.options.map { it.opt_desc }.ifEmpty { listOf("", "") }
                    imageUrls = info.options.map { it.img_url }.ifEmpty { listOf("", "") }
                    imageVote = imageUrls.any { it.isNotBlank() }
                    choiceCount = info.choice_cnt.coerceIn(1, options.size)
                    if (info.end_time > 0) endMillis = info.end_time * 1000
                },
                onFailure = { loadFailed = true; errorMessage = it.message ?: "加载投票失败" },
            )
            loading = false
        }
    }

    AppAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { AppText(if (voteId == null) "发起投票" else "编辑投票") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            ) {
                if (loading) AppText("加载中…")
                if (!loading && !loadFailed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                    AppFilterChip(selected = !imageVote, onClick = { imageVote = false }, enabled = !submitting, label = { AppText("文字投票") })
                    AppFilterChip(selected = imageVote, onClick = { imageVote = true }, enabled = !submitting, label = { AppText("图片投票") })
                }
                AppTextField(value = title, onValueChange = { if (!submitting && it.length <= 32) title = it }, placeholder = "投票标题（最多 32 字）")
                AppTextField(value = description, onValueChange = { if (!submitting && it.length <= 100) description = it }, placeholder = "补充说明（最多 100 字）", singleLine = false, minLines = 2)
                options.forEachIndexed { index, option ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
                        AppTextField(value = option, onValueChange = { value ->
                            if (!submitting && value.length <= 20) options = options.toMutableList().also { it[index] = value }
                        }, modifier = Modifier.fillMaxWidth(), placeholder = "选项 ${index + 1}（最多 20 字）")
                        if (imageVote || options.size > 2) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
                                if (imageVote) {
                                    AppTextButton(onClick = {
                                        uploadIndex = index
                                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }, enabled = !loading && !submitting) {
                                        if (imageUrls.getOrNull(index).isNullOrBlank()) AppText("添加图片")
                                        else {
                                            AsyncImage(model = imageUrls[index], contentDescription = null, modifier = Modifier.size(48.dp))
                                            AppText("更换图片")
                                        }
                                    }
                                }
                                if (options.size > 2) AppTextButton(enabled = !loading && !submitting, onClick = {
                                    options = options.toMutableList().also { it.removeAt(index) }
                                    imageUrls = imageUrls.toMutableList().also { it.removeAt(index) }
                                    choiceCount = choiceCount.coerceAtMost(options.size)
                                }) { AppText("删除选项 ${index + 1}") }
                            }
                        }
                    }
                }
                if (options.size < 20) AppTextButton(enabled = !loading && !submitting, onClick = { options = options + ""; imageUrls = imageUrls + "" }) { AppText("添加选项") }
                AppText("最多可选 $choiceCount 项")
                Row {
                    AppTextButton(enabled = !submitting && choiceCount > 1, onClick = { choiceCount = (choiceCount - 1).coerceAtLeast(1) }) { AppText("−") }
                    AppTextButton(enabled = !submitting && choiceCount < options.size, onClick = { choiceCount = (choiceCount + 1).coerceAtMost(options.size) }) { AppText("＋") }
                }
                AppTextButton(enabled = !submitting, onClick = { showDateTimePicker = true }) {
                    AppText("截止时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(endMillis)))
                }
                }
                errorMessage?.let { AppText(it, color = AppContentColors.error()) }
                if (loadFailed) {
                    AppTextButton(onClick = { loadAttempt++ }) { AppText("重新加载") }
                }
            }
        },
        confirmButton = {
            AppDialogAction(onClick = {
                if (submitting || loading || loadFailed) return@AppDialogAction
                val duration = (endMillis - System.currentTimeMillis()) / 1000L
                when {
                    title.isBlank() || options.any { it.isBlank() } -> errorMessage = "请填写标题和全部选项"
                    imageVote && imageUrls.any { it.isBlank() } -> errorMessage = "请为每个选项添加图片"
                    duration < 60 || duration > 90L * 24 * 60 * 60 -> errorMessage = "截止时间需在 1 分钟后至 90 天内"
                    else -> {
                        submitting = true
                        errorMessage = null
                        scope.launch {
                            DynamicCreateRepository.createVote(title, options, description, choiceCount, duration.toInt(), voteId, if (imageVote) imageUrls else emptyList()).fold(
                                onSuccess = { submitting = false; onCreated(it) },
                                onFailure = { submitting = false; errorMessage = it.message ?: "创建失败" },
                            )
                        }
                    }
                }
            }) { AppText(when { loading -> "加载中…"; submitting -> "保存中…"; voteId != null -> "保存"; else -> "创建" }) }
        },
        dismissButton = { AppDialogAction(onClick = { if (!submitting) onDismiss() }) { AppText("取消") } },
    )
    if (showDateTimePicker) {
        DynamicDateTimeDialog("投票截止时间", endMillis, onDismiss = { showDateTimePicker = false }, onConfirm = {
            endMillis = it
            showDateTimePicker = false
        })
    }
}
