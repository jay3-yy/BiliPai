package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.ClientDigitalIdFont
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import com.android.purebilibili.data.model.response.DecorateInfo
import com.android.purebilibili.feature.video.ui.components.resolveFanGroupLabelTextColor

@Composable
internal fun DynamicAuthorDecoration(decorate: DecorateInfo, modifier: Modifier = Modifier) {
    if (decorate.card_url.isBlank()) return
    Box(modifier.width(80.dp).height(32.dp), contentAlignment = Alignment.CenterEnd) {
        PageAwareAsyncImage(
            model = decorate.card_url,
            contentDescription = decorate.name,
            modifier = Modifier.height(32.dp),
            contentScale = ContentScale.FillHeight,
        )
        val number = decorate.fan?.displayNumber.orEmpty()
        if (number.isNotBlank()) {
            AppText(
                text = number,
                fontFamily = ClientDigitalIdFont,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                color = resolveFanGroupLabelTextColor(
                    decorate.fan?.color, MaterialTheme.colorScheme.surface, AppContentColors.primaryText(),
                ),
                maxLines = 1,
                tapToCopyEnabled = false,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp).width(48.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
