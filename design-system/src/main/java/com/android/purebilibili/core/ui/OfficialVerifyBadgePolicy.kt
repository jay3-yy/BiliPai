package com.android.purebilibili.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class OfficialVerifyBadgeTone {
    PERSONAL,
    ORGANIZATION
}

data class OfficialVerifyBadgeSpec(
    val text: String,
    val contentDescription: String,
    val tone: OfficialVerifyBadgeTone
)

fun resolveOfficialVerifyBadge(
    type: Int?,
    title: String? = null,
    desc: String? = null,
    compact: Boolean = false
): OfficialVerifyBadgeSpec? {
    val tone = when (type) {
        0 -> OfficialVerifyBadgeTone.PERSONAL
        1 -> OfficialVerifyBadgeTone.ORGANIZATION
        else -> return null
    }
    return buildOfficialVerifyBadgeSpec(
        tone = tone,
        title = title,
        desc = desc,
        compact = compact
    )
}

fun resolveOfficialVerifyBadgeFromRole(
    type: Int?,
    role: Int?,
    title: String? = null,
    desc: String? = null,
    compact: Boolean = false
): OfficialVerifyBadgeSpec? {
    if ((type ?: -1) < 0) return null
    val tone = when (role) {
        3, 4, 5, 6 -> OfficialVerifyBadgeTone.ORGANIZATION
        else -> OfficialVerifyBadgeTone.PERSONAL
    }
    return buildOfficialVerifyBadgeSpec(
        tone = tone,
        title = title,
        desc = desc,
        compact = compact
    )
}

private fun buildOfficialVerifyBadgeSpec(
    tone: OfficialVerifyBadgeTone,
    title: String?,
    desc: String?,
    compact: Boolean
): OfficialVerifyBadgeSpec {
    val fullText = title.orEmpty().ifBlank { desc.orEmpty() }.ifBlank {
        when (tone) {
            OfficialVerifyBadgeTone.PERSONAL -> "个人认证"
            OfficialVerifyBadgeTone.ORGANIZATION -> "机构认证"
        }
    }
    val visibleText = if (compact) {
        when (tone) {
            OfficialVerifyBadgeTone.PERSONAL -> "个人"
            OfficialVerifyBadgeTone.ORGANIZATION -> "机构"
        }
    } else {
        fullText
    }
    return OfficialVerifyBadgeSpec(
        text = visibleText,
        contentDescription = fullText,
        tone = tone
    )
}

@Composable
fun OfficialVerifyBadge(
    badge: OfficialVerifyBadgeSpec,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (compact) {
        OfficialVerifyAvatarBadge(badge = badge, modifier = modifier)
        return
    }
    val containerColor = when (badge.tone) {
        OfficialVerifyBadgeTone.PERSONAL -> Color(0xFFFFF3CD)
        OfficialVerifyBadgeTone.ORGANIZATION -> Color(0xFFDCEBFF)
    }
    val contentColor = when (badge.tone) {
        OfficialVerifyBadgeTone.PERSONAL -> Color(0xFF7A4B00)
        OfficialVerifyBadgeTone.ORGANIZATION -> Color(0xFF174EA6)
    }
    Surface(
        modifier = modifier.widthIn(max = 120.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = AppShapes.container(ContainerLevel.Tag)
    ) {
        Text(
            text = badge.text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 2.dp
            )
        )
    }
}

@Composable
fun OfficialVerifyAvatarBadge(
    badge: OfficialVerifyBadgeSpec,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = Icons.Outlined.Bolt,
            contentDescription = badge.contentDescription,
            tint = when (badge.tone) {
                OfficialVerifyBadgeTone.PERSONAL -> Color(0xFFFFCC00)
                OfficialVerifyBadgeTone.ORGANIZATION -> Color(0xFF40C4FF)
            },
            modifier = Modifier.size(14.dp),
        )
    }
}
