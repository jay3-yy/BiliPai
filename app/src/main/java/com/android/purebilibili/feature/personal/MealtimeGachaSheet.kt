package com.android.purebilibili.feature.personal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.core.util.FormatUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealtimeGachaSheet(
    candidates: List<MealtimeGachaCandidate>,
    sourceLabel: String,
    onPlay: (MealtimeGachaCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val menuHaptics = rememberAppHapticFeedback(AppHapticScene.MENUS)
    val confirmHaptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 88.dp.toPx() }

    val defaultFilter = remember(candidates) {
        if (filterMealtimeCandidates(candidates, MealtimeDurationFilter.MEALTIME).isNotEmpty()) {
            MealtimeDurationFilter.MEALTIME
        } else {
            MealtimeDurationFilter.ALL
        }
    }
    var selectedFilter by remember(candidates) { mutableStateOf(defaultFilter) }
    var session by remember(candidates, selectedFilter) {
        mutableStateOf(startMealtimeGachaSession(candidates, selectedFilter))
    }
    val dragOffsetX = remember { Animatable(0f) }

    LaunchedEffect(session.current?.bvid) {
        dragOffsetX.snapTo(0f)
    }

    fun redrawNext() {
        menuHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        session = drawNextMealtimeCandidate(session, candidates, selectedFilter)
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.CardGiftcard,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        AppText(
                            text = "下饭盲盒",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppContentColors.primaryText(),
                        )
                        AppText(
                            text = "从$sourceLabel 随缘抽一集，左滑或点换一张",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                }
                if (session.poolSize > 0) {
                    AppText(
                        text = "${session.drawnCount}/${session.poolSize}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.secondaryText(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealtimeDurationFilter.entries.forEach { option ->
                    AppFilterChip(
                        selected = selectedFilter == option,
                        onClick = {
                            if (selectedFilter != option) {
                                menuHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilter = option
                            }
                        },
                        label = { AppText("${option.label} · ${option.rangeHint}") },
                    )
                }
            }

            val current = session.current
            if (current == null) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppText(
                            text = "当前时长档位还没有合适视频",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppContentColors.primaryText(),
                        )
                        if (selectedFilter != MealtimeDurationFilter.ALL && candidates.isNotEmpty()) {
                            AppTextButton(
                                onClick = {
                                    menuHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = MealtimeDurationFilter.ALL
                                },
                            ) {
                                AppText("切到全部随缘")
                            }
                        }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn() togetherWith fadeOut()
                        } else {
                            (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.96f))
                        }
                    },
                    label = "mealtime_gacha_card",
                ) { card ->
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.container(ContainerLevel.Card))
                            .clickable {
                                confirmHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                                onPlay(card)
                            }
                            .graphicsLayer {
                                if (!reduceMotion) {
                                    val offset = dragOffsetX.value
                                    translationX = offset
                                    rotationZ = (offset / 48f).coerceIn(-6f, 6f)
                                    alpha = (1f - abs(offset) / (swipeThresholdPx * 3.2f)).coerceIn(0.65f, 1f)
                                }
                            }
                            .pointerInput(card.bvid, selectedFilter, candidates) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        scope.launch {
                                            dragOffsetX.snapTo((dragOffsetX.value + dragAmount).coerceIn(-260f, 260f))
                                        }
                                    },
                                    onDragEnd = {
                                        val finalOffset = dragOffsetX.value
                                        if (abs(finalOffset) >= swipeThresholdPx) {
                                            redrawNext()
                                        } else {
                                            scope.launch {
                                                dragOffsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { dragOffsetX.animateTo(0f) }
                                    },
                                )
                            },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(AppShapes.container(ContainerLevel.Card)),
                            ) {
                                AsyncImage(
                                    model = card.coverUrl,
                                    contentDescription = card.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.56f),
                                                ),
                                            ),
                                        ),
                                )
                                AppSurface(
                                    shape = AppShapes.container(ContainerLevel.Chip),
                                    color = Color.Black.copy(alpha = 0.58f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp),
                                ) {
                                    AppText(
                                        text = if (card.durationSec > 0) FormatUtils.formatDuration(card.durationSec) else "视频",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                                AppSurface(
                                    shape = AppShapes.container(ContainerLevel.Chip),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp),
                                ) {
                                    AppText(
                                        text = resolveMealtimeGachaFlavorText(card.durationSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AppText(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = AppContentColors.primaryText(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val meta = buildList {
                                    if (card.upName.isNotBlank()) add("UP ${card.upName}")
                                    if (card.viewCount > 0L) add("${FormatUtils.formatStat(card.viewCount)} 播放")
                                }.joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    AppText(
                                        text = meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppOutlinedButton(
                        onClick = { redrawNext() },
                        enabled = session.poolSize > 1,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppIcon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        AppText(" 换一张")
                    }
                    AppButton(
                        onClick = {
                            confirmHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                            onPlay(current)
                        },
                        modifier = Modifier.weight(1.35f),
                    ) {
                        AppIcon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        AppText(" 就看这个！")
                    }
                }
            }
        }
    }
}
