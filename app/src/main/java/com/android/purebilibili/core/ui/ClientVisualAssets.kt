package com.android.purebilibili.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.AppText

internal val ClientDigitalIdFont = FontFamily(Font(R.font.bili_digital_id))
private val ClientActionFont = FontFamily(Font(R.font.bili_client_icons))

internal enum class ClientDanmakuGlyph(val character: String) {
    LIKE("\uE80F"), LIKED("\uE810"), COPY("\uE80E"), RECALL("\uE811"), REPORT("\uE80D"),
}

@Composable
internal fun ClientAiSummaryIcon(modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(R.drawable.bili_ai_summary), description, modifier)
}

@Composable
internal fun ClientDanmakuActionIcon(
    glyph: ClientDanmakuGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = glyph.character,
        fontFamily = ClientActionFont,
        fontSize = 20.sp,
        lineHeight = 20.sp,
        color = tint,
        style = appIconGlyphTextStyle(),
        tapToCopyEnabled = false,
        modifier = modifier.clearAndSetSemantics { },
    )
}
