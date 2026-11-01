package com.android.purebilibili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.UnreadBadgeMode
import com.android.purebilibili.feature.home.components.HOME_TOP_RIGHT_BADGE_DOT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The header owns message updates; feed layout does not observe badge state. */
@Composable
internal fun HomeUnreadBadgeUpdates(
    unread: StateFlow<Int>,
    mode: Flow<UnreadBadgeMode>,
    content: @Composable (Int) -> Unit,
) {
    key(unread, mode) {
        val count by unread.collectAsStateWithLifecycle()
        val badgeMode by mode.collectAsStateWithLifecycle(initialValue = UnreadBadgeMode.NUMBER)
        val displayed = when (badgeMode) {
            UnreadBadgeMode.HIDDEN -> 0
            UnreadBadgeMode.DOT -> if (count > 0) HOME_TOP_RIGHT_BADGE_DOT else 0
            UnreadBadgeMode.NUMBER -> count
        }
        content(displayed)
    }
}
