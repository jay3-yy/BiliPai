package com.android.purebilibili.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Center within the caller's content area; keep retry reachable in a short viewport.
 * Unbounded list/header parents already scroll, so do not nest another vertical scroller.
 */
@Composable
fun MaidStateViewport(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = if (constraints.hasBoundedHeight) {
                Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())
            } else Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}
