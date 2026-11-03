package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.bangumi.bangumiFreeColor

/** 文字选集的权限标识不随选中态染色，封面角标使用独立的填充样式。 */
@Composable
internal fun BangumiEpisodeBadge(label: String, modifier: Modifier = Modifier) {
    if (label == "会员") {
        Image(
            painter = painterResource(R.drawable.ic_bangumi_vip),
            contentDescription = "大会员",
            modifier = modifier.size(16.dp),
        )
    } else {
        AppText(
            text = label,
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = when (label) {
                "限免", "免费" -> bangumiFreeColor()
                "预告" -> AppContentColors.secondaryText()
                else -> AppContentColors.accent()
            },
            maxLines = 1,
        )
    }
}
