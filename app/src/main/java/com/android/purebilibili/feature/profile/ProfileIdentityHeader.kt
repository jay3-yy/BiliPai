/** 居中资料头部及姓名、UID 的滚动布局。 */
package com.android.purebilibili.feature.profile

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.ui.components.UserVipBadge
import com.android.purebilibili.feature.home.UserState
import kotlin.math.roundToInt

@Composable
internal fun ProfileIdentityHeader(
    user: UserState,
    heroChrome: ProfileHeroChrome,
    collapsedTextColor: Color,
    statusBarTopPadding: Dp,
    expandedFractionProvider: () -> Float,
    statsTopProvider: () -> Float,
    collapseRangeProvider: () -> Float,
    onIdentityMeasured: (nameHeightPx: Int, uidHeightPx: Int) -> Unit,
    listState: LazyListState,
    flingBehavior: FlingBehavior,
    onAvatarClick: () -> Unit,
    avatarSourceRect: androidx.compose.runtime.MutableState<androidx.compose.ui.geometry.Rect?>,
    modifier: Modifier = Modifier,
    photoState: ProfileAvatarExpansionState? = null,
    photoFractionProvider: () -> Float = { 0f },
    photoBottomProvider: () -> Float = statsTopProvider,
    photoScrollConnection: NestedScrollConnection? = null,
    onCollapsePhoto: () -> Unit = {},
) {
    val measuredIdentityHeights = remember(LocalDensity.current, statusBarTopPadding) {
        intArrayOf(-1, -1)
    }
    Box(modifier = modifier) {
        ProfileAvatarEffect(
            user = user,
            expandedFractionProvider = expandedFractionProvider,
            statusBarTopPadding = statusBarTopPadding,
            borderColor = heroChrome.avatarBorderColor,
            listState = listState,
            flingBehavior = flingBehavior,
            onAvatarClick = onAvatarClick,
            avatarSourceRect = avatarSourceRect,
            photoScrollConnection = photoScrollConnection,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (photoFractionProvider() > 0f) 0f else 1f },
        )
        if (photoState != null) {
            ProfileExpandedAvatar(
                user = user,
                state = photoState,
                fractionProvider = photoFractionProvider,
                photoBottomProvider = photoBottomProvider,
                statusBarTopPadding = statusBarTopPadding,
                listState = listState,
                flingBehavior = flingBehavior,
                onCollapse = onCollapsePhoto,
                modifier = Modifier.matchParentSize(),
            )
        }
        Layout(
            modifier = Modifier.matchParentSize(),
            content = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        AppText(
                            text = user.name,
                            color = heroChrome.textColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = expandedFractionProvider() * (1f - photoFractionProvider()) },
                        )
                        AppText(
                            text = user.name,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .graphicsLayer { alpha = photoFractionProvider() }
                                .clearAndSetSemantics {},
                        )
                        AppText(
                            text = user.name,
                            color = collapsedTextColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .graphicsLayer { alpha = 1f - expandedFractionProvider() }
                                .clearAndSetSemantics {},
                        )
                    }
                    UserLevelBadge(level = user.level, isSeniorMember = user.isSeniorMember)
                    if (user.isVip) UserVipBadge(label = user.vipLabel, compact = true)
                }
                Box {
                    AppText(
                        text = "UID ${user.mid}",
                        color = heroChrome.secondaryTextColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = 1f - photoFractionProvider() },
                    )
                    AppText(
                        text = "UID ${user.mid}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .graphicsLayer { alpha = photoFractionProvider() }
                            .clearAndSetSemantics {},
                    )
                }
            },
        ) { measurables, constraints ->
            val expanded = expandedFractionProvider().coerceIn(0f, 1f)
            val width = constraints.maxWidth
            val top = statusBarTopPadding.toPx()
            val photo = photoFractionProvider()
            val normalNameScale = 1f + (PROFILE_EXPANDED_NAME_SCALE - 1f) * expanded
            val nameScale = normalNameScale + (1.38f - normalNameScale) * photo
            val nameMargin = 112.dp.toPx() + (36.dp.toPx() - 112.dp.toPx()) * photo
            val name = measurables[0].measure(Constraints(maxWidth = ((width - nameMargin).coerceAtLeast(0f) / nameScale).roundToInt()))
            val status = measurables[1].measure(Constraints(maxWidth = (width - 32.dp.toPx() * photo).roundToInt().coerceAtLeast(0)))
            if (measuredIdentityHeights[0] != name.height || measuredIdentityHeights[1] != status.height) {
                measuredIdentityHeights[0] = name.height
                measuredIdentityHeights[1] = status.height
                onIdentityMeasured(name.height, status.height)
            }
            // Large text grows the hero reserve downward, never moves the name into the avatar.
            val expandedNameY = top + PROFILE_EXPANDED_NAME_TOP_DP.dp.toPx()
            val collapsedNameY = top + (56.dp.toPx() - name.height) / 2f
            // Both the foreground identity and the scrolling statistics use one measured range.
            val normalNameY = collapsedNameY + (expandedNameY - collapsedNameY) * expanded
            val normalStatusY = normalNameY + name.height * nameScale + 6.dp.toPx()
            val photoStatusY = photoBottomProvider() - 10.dp.toPx() - status.height
            // Preserve the 30/10dp endpoints; measured large text may need
            // more than the default 20dp between the two baselines.
            val photoNameY = minOf(photoBottomProvider() - 30.dp.toPx(), photoStatusY) - name.height * nameScale
            // Follow a quadratic path with an 8dp control-point offset.
            fun path(start: Float, end: Float): Float {
                val control = (start + end) / 2f + 8.dp.toPx()
                return (1f - photo) * (1f - photo) * start + 2f * (1f - photo) * photo * control + photo * photo * end
            }
            val nameY = path(normalNameY, photoNameY)
            val statusY = path(normalStatusY, photoStatusY)
            val statsY = statsTopProvider() - collapseRangeProvider() * (1f - expanded) + (photoBottomProvider() - statsTopProvider())
            val statusAlpha = expanded * ((statsY - statusY - status.height - 8.dp.toPx()) / 16.dp.toPx()).coerceIn(0f, 1f) * (1f - photo) + photo
            val centeredNameX = (width - name.width * nameScale) / 2f
            val nameX = path(56.dp.toPx() + (centeredNameX - 56.dp.toPx()) * expanded, 18.dp.toPx())
            val statusX = path((width - status.width) / 2f, 16.dp.toPx())
            layout(width, constraints.maxHeight) {
                name.placeWithLayer(nameX.roundToInt(), nameY.roundToInt()) {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = nameScale
                    scaleY = nameScale
                }
                status.placeWithLayer(statusX.roundToInt(), statusY.roundToInt()) {
                    alpha = statusAlpha
                }
            }
        }
    }
}
