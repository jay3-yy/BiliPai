package com.android.purebilibili.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout

/** 顶部固定区域与列表分层；留白参与列表滚动，视口仍覆盖顶部，供模糊采样。 */
@Composable
internal fun SettingsPinnedChromeLayout(
    modifier: Modifier = Modifier,
    chrome: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val chromePlaceable = subcompose("chrome") {
            Box { chrome() }
        }.single().measure(constraints.copy(minHeight = 0))
        val contentPlaceable = subcompose("content") {
            Box { content(PaddingValues(top = chromePlaceable.height.toDp())) }
        }.single().measure(constraints)
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.placeRelative(0, 0)
            chromePlaceable.placeRelative(0, 0)
        }
    }
}
