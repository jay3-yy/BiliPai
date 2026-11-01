package com.android.purebilibili.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 搜索结果切换加载、空态和列表时，保留 Pager 用于测量的同一个页面根节点。 */
@Composable
internal fun SearchResultPageHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, propagateMinConstraints = true) {
        content()
    }
}
