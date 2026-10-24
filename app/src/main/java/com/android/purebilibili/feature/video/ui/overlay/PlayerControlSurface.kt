package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.components.LocalElasticPressEnabled
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled
import com.android.purebilibili.core.ui.components.LocalAppIconButtonGlassSurfaceProvided
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState

internal val LocalPlayerLevelGestureActive = compositionLocalOf { false }

/** Keeps native control feedback, touch ownership and media-surface content colors together. */
@Composable
internal fun PlayerControlSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable BoxScope.() -> Unit,
) {
    val feedbackEnabled = LocalComponentMotionEnabled.current && LocalElasticPressEnabled.current
    val elastic = rememberAppElasticPressState(feedbackStrength = if (shape == CircleShape) 1f else 0.6f)
    val ownsDrag = remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .appElasticPress(
                enabled = true,
                state = elastic,
                highlightShape = shape,
                // Observe the dock's own scroll gesture as well as its background drag.
                allowConsumedDrag = { feedbackEnabled || ownsDrag.value },
            )
            .pointerInput(Unit) {
                // Child scrolling gets first refusal; controls keep video gestures outside.
                detectDragGestures(
                    onDragStart = { ownsDrag.value = true },
                    onDragEnd = { ownsDrag.value = false },
                    onDragCancel = { ownsDrag.value = false },
                    onDrag = { change, _ -> change.consume() },
                )
            },
    ) {
        CompositionLocalProvider(
            LocalElasticPressEnabled provides (!feedbackEnabled && LocalElasticPressEnabled.current),
            LocalAppTopBarIconDecorationEnabled provides false,
            // Video chrome owns a dark media surface even when the surrounding app is light.
            // Preserve explicit white/active glyph colors instead of correcting them against
            // the light page behind a transparent native IconButton.
            LocalAppIconButtonGlassSurfaceProvided provides true,
            androidx.compose.material3.LocalContentColor provides Color.White,
            top.yukonga.miuix.kmp.theme.LocalContentColor provides Color.White,
        ) { content() }
    }
}

