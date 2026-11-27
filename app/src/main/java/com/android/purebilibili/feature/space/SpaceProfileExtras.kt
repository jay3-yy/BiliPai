package com.android.purebilibili.feature.space

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppContentStatePresentation
import com.android.purebilibili.core.ui.components.AppEmptyState
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.FormatUtils

@androidx.compose.runtime.Immutable
data class SpaceProfileNotice(
    val title: String,
    val text: String,
    val id: Long = System.nanoTime(),
)

enum class SpaceAccountPage { EDIT_PROFILE, LOGIN_ACTIVITY, COINS, EXPERIENCE, PRIVACY }

internal fun pinSpaceShortcut(context: Context, mid: Long, name: String): Boolean {
    val manager = context.getSystemService(ShortcutManager::class.java)
    val intent = Intent(context, com.android.purebilibili.MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("https://space.bilibili.com/$mid"))
    return manager.requestPinShortcut(
        ShortcutInfo.Builder(context, "space_$mid")
            .setShortLabel(name)
            .setIcon(Icon.createWithResource(context, context.applicationInfo.icon))
            .setIntent(intent)
            .build(),
        null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpaceProfileDialogs(
    state: SpaceUiState.Success,
    showReservations: Boolean,
    onDismissReservations: () -> Unit,
    onToggleReservation: (Long) -> Unit,
    onDismissNotice: (Long) -> Unit,
    onDismissMedals: () -> Unit,
    onDismissFollowedUsers: () -> Unit,
    onLoadMoreFollowedUsers: () -> Unit,
    onDynamicClick: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onWebClick: (String, String) -> Unit,
) {
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    if (state.showFollowedUsers) {
        AppAlertDialog(
            onDismissRequest = onDismissFollowedUsers,
            title = { AppText("我关注的人也关注了 TA") },
            text = {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.heightIn(max = maxListHeight),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.hasLoadedFollowedUsers && state.followedUsers.isEmpty()) item {
                        AppEmptyState(
                            title = "暂无共同关注信息",
                            presentation = AppContentStatePresentation.INLINE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(state.followedUsers, key = { it.mid }) { user ->
                        AppListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismissFollowedUsers(); onUserClick(user.mid) },
                            leadingContent = {
                                AsyncImage(
                                    model = FormatUtils.fixImageUrl(user.face),
                                    contentDescription = "头像",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                )
                            },
                            headlineContent = {
                                AppText(
                                    user.uname,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = user.sign.takeIf { it.isNotBlank() }?.let { sign ->
                                {
                                    AppText(
                                        sign,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                        )
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            if (state.isLoadingFollowedUsers) AppCircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else if (state.hasMoreFollowedUsers) AppTextButton(onClick = onLoadMoreFollowedUsers) { AppText("加载更多") }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = onDismissFollowedUsers) { AppText("关闭") } },
        )
    }
    if (showReservations) {
        AppAlertDialog(
            onDismissRequest = onDismissReservations,
            title = { AppText("预约 (${state.reservations.size})") },
            text = {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.heightIn(max = maxListHeight),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.reservations, key = { it.sid }) { reservation ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppText(reservation.name, style = MaterialTheme.typography.titleSmall)
                            AppText(listOfNotNull(reservation.description?.text?.takeIf { it.isNotBlank() },
                                "${FormatUtils.formatStat(reservation.total)}人预约").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                            reservation.lottery?.let { lottery ->
                                AppTextButton(onClick = { onWebClick(lottery.jumpUrl, lottery.text) }, enabled = lottery.jumpUrl.isNotBlank()) {
                                    AppText(lottery.text)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (reservation.dynamicId.isNotBlank()) {
                                    AppTextButton(onClick = { onDismissReservations(); onDynamicClick(reservation.dynamicId) }) { AppText("预约动态") }
                                }
                                AppTextButton(onClick = { onToggleReservation(reservation.sid) }, enabled = reservation.sid !in state.reservationBusyIds) {
                                    AppText(if (reservation.sid in state.reservationBusyIds) "处理中…" else if (reservation.isFollow == 1) "已预约 · 取消" else "预约")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = onDismissReservations) { AppText("关闭") } },
        )
    }
    state.medalWall?.let { wall ->
        AppAlertDialog(
            onDismissRequest = onDismissMedals,
            title = { AppText("粉丝勋章墙 (${wall.count})") },
            text = {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.heightIn(max = maxListHeight),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (wall.name.isNotBlank()) {
                        item {
                            AppText(
                                wall.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (wall.list.isEmpty()) item {
                        AppEmptyState(
                            title = "暂无粉丝勋章",
                            presentation = AppContentStatePresentation.INLINE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(wall.list, key = { it.medal?.id ?: it.targetName }) { item ->
                        AppListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismissMedals()
                                    val live = BilibiliNavigationTargetParser.parse(item.link) as? BilibiliNavigationTarget.Live
                                    if (item.liveStatus == 1 && live != null) onLiveClick(live.roomId, "", item.targetName)
                                    else item.medal?.ruid?.takeIf { it > 0 }?.let(onUserClick)
                                },
                            headlineContent = {
                                AppText(
                                    item.targetName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = item.medal?.let { medal ->
                                {
                                    val wearing = if (item.medalInfo?.wearingStatus == 1) " · 佩戴中" else ""
                                    AppText(
                                        "${medal.name} Lv.${medal.level}$wearing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            trailingContent = if (item.liveStatus == 1) {
                                {
                                    AppText(
                                        "直播中",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppContentColors.accent(),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = onDismissMedals) { AppText("关闭") } },
        )
    }
    state.profileNotice?.let { notice ->
        AppAlertDialog(onDismissRequest = { onDismissNotice(notice.id) }, title = { AppText(notice.title) },
            text = { AppText(notice.text) }, confirmButton = { AppTextButton(onClick = { onDismissNotice(notice.id) }) { AppText("关闭") } })
    }
}
