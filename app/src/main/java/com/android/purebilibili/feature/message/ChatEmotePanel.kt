package com.android.purebilibili.feature.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import com.android.purebilibili.data.model.response.EmotePackage

/** 固定面板高度，网格按可用宽度调整；选择后保留面板以便连续输入。 */
@Composable
internal fun ChatEmotePanel(
    packages: List<EmotePackage>,
    panelHeight: androidx.compose.ui.unit.Dp = 260.dp,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = packages.firstOrNull { it.id == selectedId } ?: packages.firstOrNull()
    AppSurface(modifier.height(panelHeight)) {
        Column(Modifier.fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppCircularProgressIndicator()
                }
                error != null -> AppErrorState(
                    title = "表情加载失败",
                    message = error,
                    presentation = AppContentStatePresentation.INLINE,
                    primaryAction = AppContentStateAction("重试", onRetry),
                    modifier = Modifier.fillMaxSize(),
                )
                packages.isEmpty() -> AppEmptyState(
                    title = "暂无可用表情",
                    presentation = AppContentStatePresentation.INLINE,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    val tabOptions = remember(packages) {
                        packages.map { AppSegmentOption(it.id, it.text) }
                    }
                    AppThemeAdaptiveTabRow(
                        role = AppTabRowRole.SECONDARY,
                        options = tabOptions,
                        selectedValue = selected?.id ?: 0L,
                        onSelectionChange = { selectedId = it },
                        scrollable = true,
                        // 玻璃使用完整槽位，原生标签栏保留默认指示器策略。
                        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(8.dp)) {
                        items(selected?.emote.orEmpty(), key = { it.id }) { emote ->
                            AsyncImage(emote.url, emote.text, Modifier.aspectRatio(1f).clickable { onChoose(emote.text) }.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

internal fun insertChatEmote(value: androidx.compose.ui.text.input.TextFieldValue, emote: String): androidx.compose.ui.text.input.TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    return androidx.compose.ui.text.input.TextFieldValue(
        text = value.text.replaceRange(start, end, emote),
        selection = androidx.compose.ui.text.TextRange(start + emote.length),
    )
}
