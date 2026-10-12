package com.android.purebilibili.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle

/** Typography roles shared by feed cards regardless of their visual composition. */
data class FeedContentTypography(
    val title: TextStyle,
    val author: TextStyle,
    val statistic: TextStyle,
    val coverBadge: TextStyle,
)

enum class FeedTitleHierarchy {
    Compact,
    Standard,
    Prominent,
}

@Composable
fun feedContentTypography(
    titleHierarchy: FeedTitleHierarchy = FeedTitleHierarchy.Compact,
): FeedContentTypography {
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val author = MaterialTheme.typography.labelMedium
    // 行高倍数、字重与 tabular 数字等规则在 design-tokens 的 FeedContentTextRoles 唯一维护，
    // TV 卡片用 tv-material 字阶装配同一规则；此处只保留手机 Material 字阶与 Miuix 分档。
    val statisticBase = if (isMiuix) MaterialTheme.typography.labelMedium
    else MaterialTheme.typography.labelSmall
    val badgeBase = if (isMiuix) MaterialTheme.typography.labelMedium
    else MaterialTheme.typography.labelSmall
    return FeedContentTypography(
        title = when (titleHierarchy) {
            FeedTitleHierarchy.Compact -> FeedContentTextRoles.titleCompact(bodyMedium)
            FeedTitleHierarchy.Standard -> FeedContentTextRoles.titleStandard(bodyMedium)
            FeedTitleHierarchy.Prominent -> FeedContentTextRoles.titleProminent(bodyMedium)
        },
        author = FeedContentTextRoles.author(author),
        statistic = FeedContentTextRoles.statistic(statisticBase),
        coverBadge = FeedContentTextRoles.coverBadge(badgeBase),
    )
}
