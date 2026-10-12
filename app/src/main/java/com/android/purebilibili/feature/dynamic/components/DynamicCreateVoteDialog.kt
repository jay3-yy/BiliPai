package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.data.model.response.DynamicCreatedVote
import com.android.purebilibili.data.repository.DynamicCreateRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Composable
fun DynamicCreateVoteDialog(
    onDismiss: () -> Unit,
    onCreated: (DynamicCreatedVote) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var optionOne by remember { mutableStateOf("") }
    var optionTwo by remember { mutableStateOf("") }
    var optionThree by remember { mutableStateOf("") }
    var optionFour by remember { mutableStateOf("") }
    var choiceCount by remember { mutableIntStateOf(1) }
    var durationDays by remember { mutableIntStateOf(1) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("发起投票") },
        text = {
            val liquidGlassEnabled =
                com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
            val voteChromeBackdrop = if (liquidGlassEnabled) rememberLayerBackdrop() else null
            val choiceLabels = remember { listOf("单选", "多选") }
            val durationLabels = remember { listOf("1 天", "3 天", "7 天") }
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (voteChromeBackdrop != null) {
                                Modifier.layerBackdrop(voteChromeBackdrop)
                            } else {
                                Modifier
                            }
                        )
                        .background(AppSurfaceTokens.background())
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
                ) {
                    AppTextField(value = title, onValueChange = { title = it }, placeholder = "投票标题", singleLine = true)
                    AppTextField(value = description, onValueChange = { description = it }, placeholder = "补充说明（可选）", singleLine = false, minLines = 2)
                    AppTextField(value = optionOne, onValueChange = { optionOne = it }, placeholder = "选项 1", singleLine = true)
                    AppTextField(value = optionTwo, onValueChange = { optionTwo = it }, placeholder = "选项 2", singleLine = true)
                    AppTextField(value = optionThree, onValueChange = { optionThree = it }, placeholder = "选项 3（可选）", singleLine = true)
                    AppTextField(value = optionFour, onValueChange = { optionFour = it }, placeholder = "选项 4（可选）", singleLine = true)
                    DynamicAdaptiveSegmentedControl(
                        items = choiceLabels,
                        selectedIndex = if (choiceCount == 1) 0 else 1,
                        onSelected = { index -> choiceCount = if (index == 0) 1 else 2 },
                        itemWidth = 66.dp,
                        height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
                        indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
                        labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                        backdrop = voteChromeBackdrop,
                    )
                    AppText("有效期")
                    DynamicAdaptiveSegmentedControl(
                        items = durationLabels,
                        selectedIndex = when (durationDays) { 3 -> 1; 7 -> 2; else -> 0 },
                        onSelected = { index -> durationDays = listOf(1, 3, 7).getOrElse(index) { 1 } },
                        itemWidth = 66.dp,
                        height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
                        indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
                        labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                        backdrop = voteChromeBackdrop,
                    )
                    errorMessage?.let { AppText(it) }
                }
            }
        },
        confirmButton = {
            AppDialogAction(
                onClick = {
                    if (submitting) return@AppDialogAction
                    submitting = true
                    errorMessage = null
                    scope.launch {
                        DynamicCreateRepository.createVote(
                            title = title,
                            options = listOf(optionOne, optionTwo, optionThree, optionFour),
                            description = description,
                            choiceCount = choiceCount,
                            durationSeconds = durationDays * 24 * 60 * 60
                        ).fold(
                            onSuccess = { created ->
                                submitting = false
                                onCreated(created)
                            },
                            onFailure = { error ->
                                submitting = false
                                errorMessage = error.message ?: "创建失败"
                            }
                        )
                    }
                }
            ) {
                AppText(if (submitting) "创建中…" else "创建")
            }
        },
        dismissButton = {
            AppDialogAction(onClick = onDismiss) { AppText("取消") }
        }
    )
}
