package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppRadioButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.DynamicVoteInfo
import com.android.purebilibili.data.repository.DynamicVoteRepository
import kotlinx.coroutines.launch

@Composable
fun DynamicVoteDialog(
    voteId: Long,
    dynamicId: String,
    onDismiss: () -> Unit,
    initialOptionIndex: Int? = null,
    onVoteSuccess: (DynamicVoteInfo) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var loading by remember(voteId) { mutableStateOf(true) }
    var errorMessage by remember(voteId) { mutableStateOf<String?>(null) }
    var voteInfo by remember(voteId) { mutableStateOf<DynamicVoteInfo?>(null) }
    var selectedIndexes by remember(voteId) { mutableStateOf(setOf<Int>()) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(voteId) {
        loading = true
        errorMessage = null
        DynamicVoteRepository.getVoteInfo(voteId).fold(
            onSuccess = { info ->
                voteInfo = info
                selectedIndexes = info.my_votes.toSet().ifEmpty {
                    initialOptionIndex?.takeIf { index -> info.options.any { it.opt_idx == index } }
                        ?.let { setOf(it) } ?: emptySet()
                }
            },
            onFailure = { error ->
                errorMessage = error.message ?: "投票信息加载失败"
            }
        )
        loading = false
    }

    val info = voteInfo
    val ended = info != null && info.end_time > 0L &&
        info.end_time * 1000L <= System.currentTimeMillis()
    val alreadyVoted = info?.my_votes?.isNotEmpty() == true
    val canVote = info != null && !ended && !alreadyVoted && !submitting
    val maxChoices = (info?.choice_cnt ?: 1).coerceAtLeast(1)
    val multiSelect = maxChoices > 1

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(info?.title?.ifBlank { info.desc }.orEmpty().ifBlank { "投票" })
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)
            ) {
                when {
                    loading -> AppText("正在加载投票…")
                    errorMessage != null -> AppText(errorMessage.orEmpty())
                    info != null -> {
                        if (info.desc.isNotBlank() && info.desc != info.title) {
                            AppText(info.desc)
                        }
                        AppText(
                            when {
                                ended -> "投票已结束 · ${info.join_num} 人参与"
                                alreadyVoted -> "你已投票 · ${info.join_num} 人参与"
                                multiSelect -> "最多可选 $maxChoices 项 · ${info.join_num} 人参与"
                                else -> "${info.join_num} 人参与"
                            }
                        )
                        info.options.forEach { option ->
                            val selected = option.opt_idx in selectedIndexes
                            val subtitle = if (ended || alreadyVoted) {
                                "${option.cnt} 票"
                            } else {
                                ""
                            }
                            AppListItem(
                                headlineContent = { AppText(option.opt_desc.ifBlank { "选项 ${option.opt_idx}" }) },
                                supportingContent = if (subtitle.isNotBlank()) {
                                    { AppText(subtitle) }
                                } else {
                                    null
                                },
                                trailingContent = {
                                    if (multiSelect) {
                                        AppCheckbox(
                                            checked = selected,
                                            onCheckedChange = if (canVote) {
                                                { checked ->
                                                    selectedIndexes = if (checked) {
                                                        if (selectedIndexes.size >= maxChoices) {
                                                            selectedIndexes
                                                        } else {
                                                            selectedIndexes + option.opt_idx
                                                        }
                                                    } else {
                                                        selectedIndexes - option.opt_idx
                                                    }
                                                }
                                            } else {
                                                null
                                            }
                                        )
                                    } else {
                                        AppRadioButton(
                                            selected = selected,
                                            onClick = if (canVote) {
                                                { selectedIndexes = setOf(option.opt_idx) }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .appActionClickable(enabled = canVote) {
                                        selectedIndexes = if (multiSelect) {
                                            if (selected) {
                                                selectedIndexes - option.opt_idx
                                            } else if (selectedIndexes.size < maxChoices) {
                                                selectedIndexes + option.opt_idx
                                            } else {
                                                selectedIndexes
                                            }
                                        } else {
                                            setOf(option.opt_idx)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppDialogAction(
                onClick = {
                    val indexes = selectedIndexes.toList()
                    if (loading || submitting) return@AppDialogAction
                    if (!canVote || indexes.isEmpty()) {
                        onDismiss()
                        return@AppDialogAction
                    }
                    submitting = true
                    scope.launch {
                        DynamicVoteRepository.submitVote(
                            voteId = voteId,
                            optionIndexes = indexes,
                            dynamicId = dynamicId
                        ).fold(
                            onSuccess = { result ->
                                voteInfo = result
                                selectedIndexes = result.my_votes.toSet()
                                onVoteSuccess(result)
                                submitting = false
                            },
                            onFailure = { error ->
                                errorMessage = error.message ?: "投票失败"
                                submitting = false
                            }
                        )
                    }
                }
            ) {
                AppText(
                    when {
                        submitting -> "提交中…"
                        canVote -> "投票"
                        else -> "关闭"
                    }
                )
            }
        },
        dismissButton = {
            AppDialogAction(onClick = onDismiss) {
                AppText("取消")
            }
        }
    )
}
