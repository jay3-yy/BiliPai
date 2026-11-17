package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppPredictiveDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.store.settingsDataStore
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

private val coinWithLikePreference = booleanPreferencesKey("coin_with_like")

/** 非女仆投币弹层；单次动作受对话框可见性、窗口焦点、前台状态与系统动效设置控制。 */
@Composable
internal fun CoinTossDialog(
    currentCoinCount: Int,
    userBalance: Double?,
    maxCoins: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val savedLike by remember(context) {
        context.settingsDataStore.data.map { it[coinWithLikePreference] ?: false }
    }.collectAsStateWithLifecycle(initialValue = false)
    var alsoLike by remember { mutableStateOf<Boolean?>(null) }
    val effectiveLike = alsoLike ?: savedLike
    val latestLike by rememberUpdatedState(effectiveLike)
    val latestBalance by rememberUpdatedState(userBalance)
    val latestCurrent by rememberUpdatedState(currentCoinCount)
    val latestCap by rememberUpdatedState(maxCoins)
    val latestConfirm by rememberUpdatedState(onConfirm)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val reduceMotion = rememberSystemReduceMotion()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { if (maxCoins >= 2) 2 else 1 })
    val motion = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val opacity = remember { Animatable(0f) }
    var paying by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    var paymentJob by remember { mutableStateOf<Job?>(null) }
    val frame by remember {
        derivedStateOf { resolveCoinTossFrame(motion.value, pager.currentPage + 1) }
    }
    // 只有闪电帧切换参与组合，连续位移和缩放在图层阶段读取。
    val thunderIndex by remember { derivedStateOf { frame.thunderIndex } }

    Dialog(
        onDismissRequest = {
            if (!closing) {
                closing = true
                paymentJob?.cancel()
                latestDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val originalDim = window?.attributes?.dimAmount
            window?.setDimAmount(0.5f)
            onDispose { if (originalDim != null) window?.setDimAmount(originalDim) }
        }
        val gate = rememberDecorativeAnimationGate(respectComponentMotion = false)
        val active = gate.active && !reduceMotion
        val latestActive by rememberUpdatedState(active)
        LaunchedEffect(active) {
            if (!active) {
                paymentJob?.cancel()
                paymentJob = null
                paying = false
                closing = false
                motion.snapTo(0f)
                if (entered || reduceMotion) opacity.snapTo(1f)
            } else if (!entered) {
                opacity.animateTo(1f, tween(225, easing = LinearEasing))
                entered = true
            }
        }
        LaunchedEffect(pager, active) {
            snapshotFlow { pager.currentPage }.distinctUntilChanged().collect {
                pulse.snapTo(1f)
                if (active) {
                    pulse.animateTo(1.1f, tween(50, easing = LinearEasing))
                    pulse.animateTo(1f, tween(50, easing = LinearEasing))
                }
            }
        }
        DisposableEffect(Unit) { onDispose { paymentJob?.cancel() } }

        val dismiss: () -> Unit = {
            if (!closing) {
                closing = true
                paymentJob?.cancel()
                paymentJob = scope.launch {
                    if (latestActive) opacity.animateTo(0f, tween(225, easing = LinearEasing))
                    latestDismiss()
                }
            }
        }
        val submit: () -> Unit = {
            val count = pager.currentPage + 1
            if (!paying && !closing && canGiveCoins(count, latestCurrent, latestCap, latestBalance)) {
                paying = true
                paymentJob = scope.launch {
                    try {
                        motion.snapTo(0f)
                        val animated = latestActive
                        if (animated) motion.animateTo(400f, tween(400, easing = LinearEasing))
                        if (closing || (animated && !gate.active)) return@launch
                        if (canGiveCoins(count, latestCurrent, latestCap, latestBalance)) {
                            latestConfirm(count, latestLike)
                        } else {
                            motion.snapTo(0f)
                        }
                    } finally {
                        paying = false
                    }
                }
            }
        }
        val latestSubmit by rememberUpdatedState(submit)
        val select: (Int) -> Unit = { page ->
            if (!paying && !closing && page in 0 until pager.pageCount) {
                scope.launch {
                    if (latestActive) pager.animateScrollToPage(
                        page, animationSpec = tween(200, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
                    ) else pager.scrollToPage(page)
                }
            }
        }
        val latestSelect by rememberUpdatedState(select)
        BoxWithConstraints(
            Modifier.fillMaxSize().then(gate.modifier)
                .graphicsLayer { alpha = opacity.value }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null, onClick = dismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val portrait = maxHeight >= maxWidth
            val panelWidth = if (portrait) maxWidth else minOf(525.dp, maxWidth * 0.6f)
            if (thunderIndex in 0..2 && maxCoins >= 2) {
                Image(
                    painterResource(listOf(R.drawable.coin_toss_thunder_1, R.drawable.coin_toss_thunder_2, R.drawable.coin_toss_thunder_3)[thunderIndex]),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).width(panelWidth),
                )
            }
            Column(
                modifier = Modifier.width(panelWidth)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    if (maxCoins >= 2) {
                        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
                            val pageWidth = maxWidth * 0.3f
                            HorizontalPager(
                                state = pager,
                                pageSize = PageSize.Fixed(pageWidth),
                                contentPadding = PaddingValues(horizontal = (maxWidth - pageWidth) / 2),
                                beyondViewportPageCount = 1,
                                userScrollEnabled = !paying && !closing,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                if (!paying || page == pager.currentPage) {
                                    CoinTossBox(
                                        count = page + 1,
                                        available = canGiveCoins(page + 1, currentCoinCount, maxCoins, userBalance),
                                        frameProvider = { frame },
                                        factorProvider = { (1f - abs(pager.currentPage + pager.currentPageOffsetFraction - page)).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxSize().clickable(enabled = !paying && !closing) { select(page) },
                                    )
                                }
                            }
                        }
                        if (!paying && !closing) {
                            CoinTossArrow(
                                if (pager.currentPage == 0) R.drawable.coin_toss_left_disable else R.drawable.coin_toss_left,
                                "选择一枚硬币", pager.currentPage != 0,
                                Modifier.align(Alignment.CenterStart),
                            ) { select(0) }
                            CoinTossArrow(
                                if (pager.currentPage == 1) R.drawable.coin_toss_right_disable else R.drawable.coin_toss_right,
                                "选择两枚硬币", pager.currentPage != 1,
                                Modifier.align(Alignment.CenterEnd),
                            ) { select(1) }
                        }
                    } else {
                        CoinTossBox(1, canGiveCoins(1, currentCoinCount, maxCoins, userBalance), { frame }, { 1f })
                    }
                }
                Spacer(Modifier.height(if (portrait) 25.dp else 10.dp))
                val canPay = canGiveCoins(pager.currentPage + 1, currentCoinCount, maxCoins, userBalance)
                Image(
                    painterResource(when {
                        !canPay -> R.drawable.coin_toss_22_not_enough_pay
                        pager.currentPage == 0 -> R.drawable.coin_toss_22_mario
                        else -> R.drawable.coin_toss_22_gun_sister
                    }),
                    contentDescription = if (canPay) "点击或向上滑动投${pager.currentPage + 1}枚硬币" else "当前数量不可投币",
                    modifier = Modifier.size(110.dp, 155.dp)
                        .graphicsLayer {
                            scaleX = pulse.value
                            scaleY = pulse.value
                            translationY = -size.height * frame.characterLift
                        }
                        .clickable(enabled = canPay && !paying && !closing, role = Role.Button, onClick = submit)
                        .pointerInput(canPay, paying, closing) {
                            var drag = Offset.Zero
                            var handled = false
                            detectDragGestures(
                                onDragStart = { drag = Offset.Zero; handled = false },
                                onDrag = { change, delta ->
                                    if (!paying && !closing && !handled) {
                                        drag += delta
                                        val threshold = 20.dp.toPx()
                                        if (abs(drag.x) >= threshold && abs(drag.x) > abs(drag.y)) {
                                            handled = true
                                            change.consume()
                                            latestSelect(if (drag.x < 0) 1 else 0)
                                        } else if (canPay && drag.y <= -threshold) {
                                            handled = true
                                            change.consume()
                                            latestSubmit()
                                        }
                                    }
                                },
                            )
                        },
                )
                if (userBalance != null || currentCoinCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    AppText(
                        listOfNotNull(
                            userBalance?.let { if (it < 0) "硬币余额暂不可用" else "硬币余额：${String.format(java.util.Locale.ROOT, "%.1f", it)}" },
                            "已投${currentCoinCount}枚硬币".takeIf { currentCoinCount > 0 },
                        ).joinToString("，"),
                        color = Color.White, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 30.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.align(Alignment.CenterStart)
                            .clickable(enabled = !closing) {
                                val value = !effectiveLike
                                alsoLike = value
                                scope.launch { context.settingsDataStore.edit { it[coinWithLikePreference] = value } }
                            }.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            if (effectiveLike) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = "同时点赞", tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                        AppText(" 同时点赞", color = Color.White, fontSize = 14.sp)
                    }
                    Image(
                        painterResource(R.drawable.coin_toss_panel_close), "关闭投币面板",
                        Modifier.size(30.dp).clickable(role = Role.Button, onClick = dismiss),
                    )
                }
                Spacer(Modifier.height(if (portrait) 50.dp else 10.dp))
            }
        }
    }
}

@Composable
private fun CoinTossArrow(resource: Int, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(resource), label, Modifier.size(16.dp, 28.dp))
    }
}

@Composable
private fun CoinTossBox(
    count: Int,
    available: Boolean,
    frameProvider: () -> CoinTossFrame,
    factorProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val filter = if (available) null else ColorFilter.tint(Color(0x66000000), BlendMode.SrcAtop)
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.coin_toss_pay_coins_box), null,
            Modifier.size(100.dp).graphicsLayer {
                val scale = 0.7f + factorProvider() * 0.3f
                scaleX = scale
                scaleY = scale
                translationY = -size.height * scale * frameProvider().boxLift
            }, colorFilter = filter,
        )
        Image(
            painterResource(if (count == 1) R.drawable.coin_toss_coins_one else R.drawable.coin_toss_coins_two),
            "$count 枚硬币",
            Modifier.size(50.dp).graphicsLayer {
                val scale = 0.7f + factorProvider() * 0.3f
                scaleX = scale
                scaleY = scale
                translationY = -size.height * scale * frameProvider().coinLift
                alpha = frameProvider().coinAlpha
            }, colorFilter = filter,
        )
    }
}
