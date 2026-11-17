package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText as Text
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.resolveAppExpandedContentDialogLayoutPolicy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.feature.plugin.SponsorBlockSegmentBehavior
import com.android.purebilibili.feature.plugin.defaultSponsorBlockCategoryColor

internal data class SponsorSegmentInfoRow(
    val title: String,
    val timeRange: String,
    val behaviorLabel: String,
    val seekTargetMs: Long?,
    val seekDescription: String,
)

internal fun resolveSponsorSegmentInfoRow(
    marker: SponsorProgressMarker,
    behavior: SponsorBlockSegmentBehavior,
): SponsorSegmentInfoRow = SponsorSegmentInfoRow(
    title = SponsorCategory.getSponsorVideoLabelTitle(marker.category),
    timeRange = "${formatSponsorSegmentInfoTime(marker.startTimeMs)} 至 ${formatSponsorSegmentInfoTime(marker.endTimeMs)}",
    behaviorLabel = when (behavior) {
        SponsorBlockSegmentBehavior.AUTOMATIC -> "总是跳过"
        SponsorBlockSegmentBehavior.SKIP_ONCE -> "跳过一次"
        SponsorBlockSegmentBehavior.MANUAL -> "手动跳过"
        SponsorBlockSegmentBehavior.MARKER_ONLY -> "仅显示"
        SponsorBlockSegmentBehavior.DISABLED -> "禁用"
    },
    // BlockMixin.onSkip seeks the end for both skip and show-only rows.
    seekTargetMs = marker.endTimeMs.takeUnless { it == 0L },
    seekDescription = if (behavior == SponsorBlockSegmentBehavior.MARKER_ONLY) "跳至此片段" else "跳过此片段",
)

internal fun formatSponsorSegmentInfoTime(milliseconds: Long): String {
    if (milliseconds == 0L) return "00:00"
    val seconds = milliseconds / 1000L
    val minutes = (seconds / 60L % 60L).toString().padStart(2, '0')
    val secondsText = (seconds % 60L).toString().padStart(2, '0') + "." +
        (milliseconds % 1000L).toString().padStart(3, '0')
    val hours = seconds / 3600L
    return if (hours == 0L) "$minutes:$secondsText" else "${hours.toString().padStart(2, '0')}:$minutes:$secondsText"
}

/**
 * 片段列表、投票与类别选择共用标准对话框。
 * 操作由调用方生命周期作用域提交；关闭面板不会取消已经发出的投票。
 */
@Composable
fun PlayerSponsorSegmentsPanel(
    markers: List<SponsorProgressMarker>,
    behaviorForCategory: (String) -> SponsorBlockSegmentBehavior,
    onSeek: (Long) -> Unit,
    onVote: (segmentId: String, voteType: Int) -> Unit,
    onChangeCategory: (segmentId: String, category: String) -> Unit,
    onDismiss: () -> Unit,
    allowVoting: Boolean = true,
    categoryColors: Map<String, String> = emptyMap(),
) {
    var selectedSegment by remember { mutableStateOf<SponsorProgressMarker?>(null) }
    var choosingCategory by remember { mutableStateOf(false) }
    val selected = selectedSegment
    SponsorInfoDialog(onDismiss = onDismiss, paneTitle = when {
        choosingCategory -> "更改类别"
        selected != null -> "片段投票"
        else -> "片段信息"
    }) {
        when {
            selected == null -> markers.forEach { marker ->
                val row = resolveSponsorSegmentInfoRow(marker, behaviorForCategory(marker.category))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .clickable(role = Role.Button) {
                            if (allowVoting) selectedSegment = marker else onDismiss()
                        }
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SponsorCategoryTitle(row.title, sponsorInfoColor(marker.category, marker.colorHex))
                        Text(row.timeRange, fontSize = 13.sp, lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(row.behaviorLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.seekTargetMs != null) {
                        Box(
                            Modifier.size(36.dp).semantics { contentDescription = row.seekDescription }
                                .clickable(role = Role.Button) {
                                    onDismiss()
                                    onSeek(row.seekTargetMs)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            if (behaviorForCategory(marker.category) == SponsorBlockSegmentBehavior.MARKER_ONLY) {
                                Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp), tint)
                            } else SponsorStepOverIcon(tint)
                        }
                    } else Spacer(Modifier.width(10.dp))
                }
            }
            choosingCategory -> SponsorCategory.ALL_CATEGORIES.forEach { category ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button) {
                            onChangeCategory(selected.segmentId, category)
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SponsorCategoryTitle(
                        SponsorCategory.getSponsorVideoLabelTitle(category),
                        sponsorInfoColor(category, categoryColors[category]),
                    )
                }
            }
            else -> {
                SponsorVoteOption("赞成票") {
                    onVote(selected.segmentId, 1)
                    onDismiss()
                }
                SponsorVoteOption("反对票") {
                    onVote(selected.segmentId, 0)
                    onDismiss()
                }
                SponsorVoteOption("更改类别") { choosingCategory = true }
            }
        }
    }
}

@Composable
private fun SponsorInfoDialog(onDismiss: () -> Unit, paneTitle: String, content: @Composable ColumnScope.() -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        contentLayout = remember { resolveAppExpandedContentDialogLayoutPolicy() },
        title = { AppText(paneTitle) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .semantics { this.paneTitle = paneTitle },
                content = content,
            )
        },
        confirmButton = {
            AppDialogAction(onClick = onDismiss) { AppText("关闭") }
        },
    )
}

@Composable
private fun SponsorCategoryTitle(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(title, fontSize = 14.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun SponsorVoteOption(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp))
}

private fun sponsorInfoColor(category: String, configuredHex: String?): Color {
    val hex = configuredHex?.takeIf { it.length == 7 && it.startsWith('#') }
    val rgb = hex?.substring(1)?.toLongOrNull(16)
        ?: defaultSponsorBlockCategoryColor(category).substring(1).toLong(16)
    return Color(0xFF000000L or rgb)
}

// Material Design Icons debug-step-over, also used by PiliPlus' segment dialog.
// Source: https://github.com/Templarian/MaterialDesign/blob/master/svg/debug-step-over.svg
private val SponsorStepOverVector = ImageVector.Builder(
    name = "SponsorDebugStepOver", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString(
        "M12,14A2,2 0 0,1 14,16A2,2 0 0,1 12,18A2,2 0 0,1 10,16A2,2 0 0,1 12,14M23.46,8.86L21.87,15.75L15,14.16L18.8,11.78C17.39,9.5 14.87,8 12,8C8.05,8 4.77,10.86 4.12,14.63L2.15,14.28C2.96,9.58 7.06,6 12,6C15.58,6 18.73,7.89 20.5,10.72L23.46,8.86Z",
    ).toNodes(),
    fill = SolidColor(Color.Black),
).build()

@Composable
private fun SponsorStepOverIcon(tint: Color) {
    Icon(SponsorStepOverVector, null, Modifier.size(18.dp), tint)
}
