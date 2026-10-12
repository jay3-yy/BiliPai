package com.android.purebilibili.feature.audio.lyrics.halcyon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact cover-pane lyric preview (Halcyon `PlayerMiniLyrics` surface used by BiliPai).
 * Rendering goes through the ported karaoke `TimedLyricText` so fill/lift match the lyric page.
 */
@Composable
internal fun PlayerMiniLyrics(
    lines: List<HalcyonLyricLine>,
    activeIndex: Int,
    positionMs: Long,
    contentColor: Color,
    showTranslation: Boolean = true,
    fontScale: Float = 1f,
    fontFamily: FontFamily? = null,
    onOpenLyrics: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val current = lines.getOrNull(activeIndex)
    val prev = lines.getOrNull(activeIndex - 1)
    val next = lines.getOrNull(activeIndex + 1)
    val next2 = lines.getOrNull(activeIndex + 2)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        if (current != null) {
            if (prev != null) {
                BasicText(
                    text = prev.text,
                    style = TextStyle(
                        color = contentColor.copy(alpha = 0.38f),
                        fontSize = 14.sp * fontScale,
                        fontFamily = fontFamily,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val words = remember(current) { current.words }
            TimedLyricText(
                text = current.text,
                words = words,
                positionMs = positionMs,
                active = true,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 18.sp * fontScale,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp * fontScale,
                    fontFamily = fontFamily,
                ),
                contentColor = contentColor,
                wordLiftEnabled = true,
                onWordClick = null,
            )
            val translation = current.translation
            if (showTranslation && !translation.isNullOrBlank()) {
                BasicText(
                    text = translation,
                    style = TextStyle(
                        color = contentColor.copy(alpha = 0.72f),
                        fontSize = 13.sp * fontScale,
                        fontFamily = fontFamily,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            next?.let {
                BasicText(
                    text = it.text,
                    style = TextStyle(
                        color = contentColor.copy(alpha = 0.55f),
                        fontSize = 14.sp * fontScale,
                        fontFamily = fontFamily,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (current.translation.isNullOrBlank() && next2 != null) {
                BasicText(
                    text = next2.text,
                    style = TextStyle(
                        color = contentColor.copy(alpha = 0.32f),
                        fontSize = 13.sp * fontScale,
                        fontFamily = fontFamily,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onOpenLyrics != null) {
            BasicText(
                text = "轻点查看完整歌词",
                style = TextStyle(
                    color = contentColor.copy(alpha = 0.70f),
                    fontSize = androidx.compose.material3.MaterialTheme.typography.labelSmall.fontSize,
                    fontFamily = fontFamily,
                ),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onOpenLyrics),
            )
        }
    }
}
