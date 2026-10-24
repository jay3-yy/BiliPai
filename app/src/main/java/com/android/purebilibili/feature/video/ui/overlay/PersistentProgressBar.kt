package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape

internal fun resolvePersistentProgressFraction(current: Long, duration: Long): Float {
    return resolveProgressFraction(
        positionMs = current,
        durationMs = duration
    )
}

@Composable
internal fun PersistentBottomProgressBar(
    current: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    val activeColor = AppContentColors.selectionAccent(false)
    val progress = resolvePersistentProgressFraction(current = current, duration = duration)
    if (duration <= 0L) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                color = Color.White.copy(alpha = 0.3f),
                shape = RectangleShape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(activeColor)
        )
    }
}
