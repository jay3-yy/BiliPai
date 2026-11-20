package com.android.purebilibili.feature.article

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics

@Composable
internal fun ArticleOutlineDialog(
    entries: List<ArticleOutlineEntry>,
    currentBlock: Int?,
    onSelect: (ArticleOutlineEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp - 200).coerceIn(48, 420).dp
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = entries.indexOfFirst { it.blockIndex == currentBlock }.coerceAtLeast(0),
    )
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("正文目录") },
        text = {
            if (entries.isEmpty()) {
                AppText("本文暂无标题目录", color = AppContentColors.secondaryText())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight).appScrollBoundaryHaptics(listState),
                    state = listState,
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.blockIndex }) { index, entry ->
                        val isCurrent = entry.blockIndex == currentBlock
                        AppTextButton(
                            onClick = { onSelect(entry) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                                selected = isCurrent
                                contentDescription = "${index + 1}. ${entry.title}" + if (isCurrent) "，正在阅读" else ""
                            },
                        ) {
                            AppText(
                                "${index + 1}. ${entry.title}",
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else AppContentColors.primaryText(),
                                textAlign = TextAlign.Start,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                tapToCopyEnabled = false,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { AppDialogAction(onClick = onDismiss) { AppText("关闭", tapToCopyEnabled = false) } },
    )
}
