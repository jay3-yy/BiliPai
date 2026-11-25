package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import com.android.purebilibili.core.ui.components.LocalAppTextSearchQuery
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun SettingsItemAnchor(
    settingId: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val request by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    var highlighted by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable(settingId) { mutableStateOf("") }
    LaunchedEffect(request?.token, settingId) {
        val current = request ?: return@LaunchedEffect
        if (current.settingId != settingId || !SettingsSearchFocusController.claim(current.token)) return@LaunchedEffect
        try {
            searchQuery = current.query
            keyboard?.hide()
            withFrameNanos { }
            bringIntoView.bringIntoView()
            highlighted = true
            view.announceForAccessibility("已定位到${settingsItemDirectory.firstOrNull { it.settingId == settingId }?.title.orEmpty()}")
            delay(SettingsSearchHighlightTiming.HOLD_MILLIS)
        } finally {
            highlighted = false
            SettingsSearchFocusController.clear(current.token)
        }
    }
    val highlightProgress = animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(SettingsSearchHighlightTiming.FADE_MILLIS),
        label = "settingsSearchRowHighlight",
    )
    val highlightColor = AppContentColors.accent()
    Column(
        modifier.fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .drawWithContent {
                drawContent()
                // Draw above the preference surface so native row backgrounds cannot hide it.
                val progress = highlightProgress.value
                if (progress > 0f) {
                    val radius = CornerRadius(12.dp.toPx())
                    drawRoundRect(color = highlightColor.copy(alpha = 0.12f * progress), cornerRadius = radius)
                    drawRoundRect(
                        color = highlightColor.copy(alpha = 0.45f * progress),
                        cornerRadius = radius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
    ) {
        CompositionLocalProvider(LocalAppTextSearchQuery provides searchQuery) { content() }
    }
}
