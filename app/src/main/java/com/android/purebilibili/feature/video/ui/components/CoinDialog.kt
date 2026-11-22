package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppPredictiveDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sin

internal fun canGiveCoins(count: Int, current: Int, cap: Int, balance: Double?): Boolean =
    count in 1..(cap - current).coerceAtLeast(0) &&
        (balance == null || (balance >= 0 && balance >= count))

/** 选币弹层共用同一套内容；复选框与按钮继续由 MD3/Miuix/玻璃组件负责材质。 */
@Composable
fun CoinDialog(
    visible: Boolean,
    currentCoinCount: Int,
    userBalance: Double?,
    maxCoins: Int = 2,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, alsoLike: Boolean) -> Unit,
    contentKey: Any? = null,
) {
    if (!visible) return
    val maidEnabled = com.android.purebilibili.core.ui.LocalMaidLottieAnimationEnabled.current
    key(contentKey) {
        if (maidEnabled) {
            CoinDialogContent(currentCoinCount, userBalance, maxCoins, onDismiss, onConfirm)
        } else {
            CoinTossDialog(currentCoinCount, userBalance, maxCoins, onDismiss, onConfirm)
        }
    }
}

@Composable
private fun CoinDialogContent(
    currentCoinCount: Int,
    userBalance: Double?,
    maxCoins: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit,
) {
    var selectedCount by remember(currentCoinCount, maxCoins) { mutableIntStateOf(1) }
    var alsoLike by remember { mutableStateOf(true) }
    var paying by remember { mutableStateOf(false) }
    var paymentJob by remember { mutableStateOf<Job?>(null) }
    val toss = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestConfirm by rememberUpdatedState(onConfirm)
    val latestBalance by rememberUpdatedState(userBalance)
    val latestCurrent by rememberUpdatedState(currentCoinCount)
    val latestCap by rememberUpdatedState(maxCoins)
    val reduceMotion = rememberSystemReduceMotion()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val gate = rememberDecorativeAnimationGate(respectComponentMotion = false)
        val active = gate.active && !reduceMotion
        // 只在用户操作后运行有限时间轴；失焦、后台、离开视口时取消，不恢复旧提交。
        LaunchedEffect(active) {
            if (!active) {
                paymentJob?.cancel()
                paymentJob = null
                paying = false
                toss.snapTo(0f)
            }
        }
        DisposableEffect(Unit) { onDispose { paymentJob?.cancel() } }
        val canPay = !paying && canGiveCoins(selectedCount, currentCoinCount, maxCoins, userBalance)
        val submit: () -> Unit = {
            if (!paying && canGiveCoins(selectedCount, latestCurrent, latestCap, latestBalance)) {
                val count = selectedCount
                val like = alsoLike
                paying = true
                paymentJob = scope.launch {
                    try {
                        if (active) {
                            toss.snapTo(0f)
                            toss.animateTo(1f, tween(400))
                        }
                        if (canGiveCoins(count, latestCurrent, latestCap, latestBalance)) {
                            latestConfirm(count, like)
                        }
                    } finally {
                        paying = false
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .then(gate.modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText("投币", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().height(168.dp)
                    .pointerInput(maxCoins, currentCoinCount, paying) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f },
                            onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                            onDragEnd = {
                                if (!paying && maxCoins >= 2 && kotlin.math.abs(distance) > 40.dp.toPx()) {
                                    selectedCount = if (distance < 0) 2 else 1
                                }
                            },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (1..maxCoins.coerceIn(1, 2)).forEach { count ->
                    val available = canGiveCoins(count, currentCoinCount, maxCoins, userBalance)
                    val scale = if (active) animateFloatAsState(
                        if (selectedCount == count) 1f else 0.78f,
                        animationSpec = tween(180),
                        label = "coinSelection",
                    ) else rememberUpdatedState(if (selectedCount == count) 1f else 0.78f)
                    CoinChoice(
                        count = count,
                        selected = selectedCount == count,
                        available = available,
                        modifier = Modifier.weight(1f, fill = false).sizeIn(maxWidth = 148.dp, maxHeight = 148.dp)
                            .aspectRatio(1f).graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                alpha = if (available) 1f else 0.45f
                                if (paying && selectedCount == count) {
                                    translationY = -sin(toss.value * Math.PI).toFloat() * 10.dp.toPx()
                                }
                            },
                        coinModifier = Modifier.graphicsLayer {
                            if (paying && selectedCount == count) {
                                translationY = -(toss.value - 0.25f).coerceIn(0f, 0.6f) / 0.6f * 104.dp.toPx()
                                alpha = (1f - (toss.value - 0.75f).coerceAtLeast(0f) * 4f).coerceIn(0f, 1f)
                            }
                        },
                        onClick = { if (!paying) selectedCount = count },
                    )
                }
            }
            if (currentCoinCount > 0) AppText("已投 $currentCoinCount 枚硬币", color = Color.White)
            if (maxCoins == 1) AppText("转载视频最多可投 1 枚硬币", color = Color.White)
            Spacer(Modifier.height(16.dp))
            Image(
                painterResource(com.android.bilipai.brandmotion.R.drawable.bilipai_maid_coin_static),
                contentDescription = "点击角色投币",
                modifier = Modifier.size(176.dp).graphicsLayer {
                    translationY = if (paying) -sin((toss.value / 0.3f).coerceIn(0f, 1f) * Math.PI).toFloat() * 24.dp.toPx() else 0f
                }.clickable(enabled = canPay, role = Role.Button, onClick = submit),
            )
            AppText(
                text = when {
                    userBalance == null -> "硬币余额：加载中…"
                    userBalance < 0 -> "硬币余额暂不可用"
                    else -> "硬币余额：${String.format(java.util.Locale.ROOT, "%.1f", userBalance)}"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            if (!canGiveCoins(selectedCount, currentCoinCount, maxCoins, userBalance)) {
                AppText(
                    if (maxCoins - currentCoinCount < selectedCount) "当前视频剩余额度不足" else "硬币余额不足或暂不可用",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppCheckbox(checked = alsoLike, onCheckedChange = { if (!paying) alsoLike = it })
                AppText("同时点赞", color = Color.White, modifier = Modifier.clickable(enabled = !paying) { alsoLike = !alsoLike })
            }
            AppButton(onClick = submit, enabled = canPay) { AppText(if (paying) "投币中…" else "投 $selectedCount 枚硬币") }
            AppTextButton(onClick = onDismiss) { AppText("关闭", color = Color.White) }
        }
    }
}

@Composable
private fun CoinChoice(
    count: Int,
    selected: Boolean,
    available: Boolean,
    modifier: Modifier,
    coinModifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.semantics { this.selected = selected; contentDescription = "$count 硬币${if (available) "" else "，不可投"}" }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .background(Color(0xFFC48A52), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(Color(0xFFE7B781), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(3.dp.toPx()))
            listOf(Offset(12.dp.toPx(), 12.dp.toPx()), Offset(size.width - 12.dp.toPx(), 12.dp.toPx()),
                Offset(12.dp.toPx(), size.height - 12.dp.toPx()), Offset(size.width - 12.dp.toPx(), size.height - 12.dp.toPx())).forEach {
                drawCircle(Color(0xFF98603D), 4.dp.toPx(), it)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(coinModifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    if (count == 2) drawOval(Color(0xFFB5B9BC), topLeft = Offset(0f, size.height * 0.3f), size = Size(size.width * 0.65f, size.height * 0.65f))
                    drawCircle(Color(0xFFE4E7E9), size.minDimension * 0.36f)
                    drawCircle(Color(0xFF8C9398), size.minDimension * 0.36f, style = Stroke(2.dp.toPx()))
                    drawCircle(Color(0xFFBBC1C5), size.minDimension * 0.28f, style = Stroke(1.dp.toPx()))
                }
                AppText("币", color = Color(0xFF848B90), fontWeight = FontWeight.Bold)
            }
            AppText("$count 硬币", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
