package com.android.purebilibili.feature.home.components.cards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpOffset
import com.android.purebilibili.data.model.response.AiSummaryData

/** Created on open; retained while closing so native and glass popups can finish their exit. */
internal class VideoCardMenuState(
    val offset: DpOffset,
    val fromOverflowButton: Boolean,
) {
    var expanded by mutableStateOf(true)
}

/** A request owns its result even if the card identity changes before it finishes. */
internal class VideoCardAiSummaryState {
    var visible by mutableStateOf(true)
    var loading by mutableStateOf(true)
    var data by mutableStateOf<AiSummaryData?>(null)
    var error by mutableStateOf<String?>(null)
}
