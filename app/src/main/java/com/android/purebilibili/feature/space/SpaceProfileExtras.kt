package com.android.purebilibili.feature.space

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.FormatUtils

data class SpaceProfileNotice(val title: String, val text: String)

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
    onDismissNotice: () -> Unit,
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
                LazyColumn(Modifier.heightIn(max = maxListHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.hasLoadedFollowedUsers && state.followedUsers.isEmpty()) item { AppText("暂无共同关注信息") }
                    items(state.followedUsers, key = { it.mid }) { user ->
                        AppTextButton(onClick = { onDismissFollowedUsers(); onUserClick(user.mid) }) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppText(user.uname)
                                if (user.sign.isNotBlank()) AppText(user.sign, style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(), maxLines = 2)
                            }
                        }
                    }
                    item {
                        if (state.isLoadingFollowedUsers) AppText("正在加载…")
                        else if (state.hasMoreFollowedUsers) AppTextButton(onClick = onLoadMoreFollowedUsers) { AppText("加载更多") }
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
                LazyColumn(Modifier.heightIn(max = maxListHeight), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                LazyColumn(Modifier.heightIn(max = maxListHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { AppText(wall.name) }
                    if (wall.list.isEmpty()) item { AppText("暂无粉丝勋章") }
                    items(wall.list, key = { it.medal?.id ?: it.targetName }) { item ->
                        AppTextButton(onClick = {
                            onDismissMedals()
                            val live = BilibiliNavigationTargetParser.parse(item.link) as? BilibiliNavigationTarget.Live
                            if (item.liveStatus == 1 && live != null) onLiveClick(live.roomId, "", item.targetName)
                            else item.medal?.ruid?.takeIf { it > 0 }?.let(onUserClick)
                        }) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppText(item.targetName + if (item.liveStatus == 1) " · 直播中" else "")
                                item.medal?.let { medal ->
                                    AppText("${medal.name} ${medal.level}" + if (item.medalInfo?.wearingStatus == 1) " · 佩戴中" else "",
                                        style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = onDismissMedals) { AppText("关闭") } },
        )
    }
    state.profileNotice?.let { notice ->
        AppAlertDialog(onDismissRequest = onDismissNotice, title = { AppText(notice.title) },
            text = { AppText(notice.text) }, confirmButton = { AppTextButton(onClick = onDismissNotice) { AppText("关闭") } })
    }
}
