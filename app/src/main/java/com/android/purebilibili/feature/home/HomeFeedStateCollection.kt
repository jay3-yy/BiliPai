package com.android.purebilibili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Freeze the displayed state while a navigation transition owns the home surface.
 * The producer keeps its latest value; resuming collects that value without replaying
 * intermediate pages. A different source starts from its own cache, even while paused.
 */
@Composable
internal fun <T> StateFlow<T>.collectAsHomeFeedState(updatesAllowed: Boolean): State<T> {
    val source = this
    return key(source) {
        val updates = remember(source, updatesAllowed) {
            if (updatesAllowed) source else emptyFlow<T>()
        }
        updates.collectAsStateWithLifecycle(initialValue = source.value)
    }
}

private fun isHomeChromeContentReady(content: CategoryContent): Boolean =
    !(content.isLoading && content.videos.isEmpty() && content.liveRooms.isEmpty())

/** Background consumers need readiness, not every page, loading flag or error update. */
@Composable
internal fun StateFlow<CategoryContent>.collectHomeChromeContentReady(
    updatesAllowed: Boolean,
): State<Boolean> {
    val source = this
    return key(source) {
        val updates = remember(source, updatesAllowed) {
            if (updatesAllowed) source.map(::isHomeChromeContentReady).distinctUntilChanged()
            else emptyFlow<Boolean>()
        }
        updates.collectAsStateWithLifecycle(initialValue = isHomeChromeContentReady(source.value))
    }
}
