package com.android.purebilibili.feature.plugin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.input.gamepad.GamepadActionSlot
import com.android.purebilibili.core.input.gamepad.GamepadConfig
import com.android.purebilibili.core.input.gamepad.GamepadControlEngine
import com.android.purebilibili.core.input.gamepad.GamepadControlMode
import com.android.purebilibili.core.input.gamepad.GamepadControllerProfile
import com.android.purebilibili.core.input.gamepad.GamepadCursorColorStyle
import com.android.purebilibili.core.input.gamepad.GamepadKeyCodes
import com.android.purebilibili.core.input.gamepad.GamepadScene
import com.android.purebilibili.core.input.gamepad.formatGamepadKeyBadge
import com.android.purebilibili.core.input.gamepad.formatTriggerPairLabels
import com.android.purebilibili.core.input.gamepad.resolveBoundKeyCode
import com.android.purebilibili.core.ui.components.AppText

/**
 * 全局手柄视觉覆盖层（挂载于 MainActivity 与 VideoActivity 最顶层，不拦截任何触控事件）：
 * 1. 绘制丝滑虚拟光标（独立订阅高频 cursorState 并在绘制阶段读取坐标，不引起顶部/底部 HUD 重组）；
 * 2. 显示顶部轻提示条（手柄连接/断开、模式切换、倍速提示）；
 * 3. 可选底部紧凑键位提示 HUD（按当前手柄品牌 Xbox / PS / Switch 与自定义键位实时展示，可在设置中隐藏）。
 */
@Composable
fun GamepadOverlayHost(
    modifier: Modifier = Modifier,
) {
    val state by GamepadControlEngine.uiState.collectAsStateWithLifecycle()
    val config by GamepadControlEngine.configFlow.collectAsStateWithLifecycle()
    if (!state.pluginEnabled) return

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 顶部状态轻提示
        AnimatedVisibility(
            visible = !state.statusBanner.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
        ) {
            val text = state.statusBanner.orEmpty()
            Box(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                AppText(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 2. 底部紧凑键位提示条（仅连接手柄且未开启「隐藏底部键位显示」时展示）
        val showHud = state.connected && state.hudVisible && config.showLegendHud
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val hints = remember(state.scene, state.mode, state.cursorVisible, state.effectiveProfile, config) {
                resolveLegendHints(
                    scene = state.scene,
                    mode = state.mode,
                    cursorVisible = state.cursorVisible,
                    profile = state.effectiveProfile,
                    config = config,
                )
            }
            Row(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.64f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .border(
                        width = 0.7.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                hints.forEach { (keyBadge, actionLabel) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            AppText(
                                text = keyBadge,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        AppText(
                            text = actionLabel,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // 3. 虚拟光标（独立子组件，高频坐标仅在 Canvas 绘制阶段读取）
        GamepadCursorOverlay(config = config)
    }
}

@Composable
private fun GamepadCursorOverlay(
    config: GamepadConfig,
) {
    val cursorStateHolder = GamepadControlEngine.cursorState.collectAsStateWithLifecycle()
    val cursorAlphaState = animateFloatAsState(
        targetValue = if (cursorStateHolder.value.visible) 1f else 0f,
        label = "gamepad_cursor_alpha",
    )
    val shouldDrawCursor by remember {
        derivedStateOf { cursorAlphaState.value > 0.01f }
    }
    if (!shouldDrawCursor) return

    val scale = config.cursorSize.radiusScale
    val idleColor = resolveCursorIdleColor(config.cursorColorStyle)
    val isBiliPink = config.cursorColorStyle == GamepadCursorColorStyle.BILI_PINK

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cursorAlpha = cursorAlphaState.value
        val cursor = cursorStateHolder.value
        val center = Offset(cursor.x, cursor.y)
        val pressed = cursor.pressed
        val precision = cursor.precisionAimHeld
        val outerRadius = when {
            pressed -> 13.dp.toPx() * scale
            precision -> 11.dp.toPx() * scale
            else -> 15.dp.toPx() * scale
        }
        val accentColor = when {
            pressed -> if (isBiliPink) Color.White else Color(0xFFFB7299)
            precision -> Color(0xFF38BDF8)
            else -> idleColor
        }
        drawCircle(
            color = if (pressed) {
                Color(0xFFFB7299).copy(alpha = 0.28f * cursorAlpha)
            } else {
                Color.Black.copy(alpha = 0.24f * cursorAlpha)
            },
            radius = outerRadius + 3.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.94f * cursorAlpha),
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.4.dp.toPx()),
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.96f * cursorAlpha),
            radius = 2.8.dp.toPx() * scale,
            center = center,
        )
    }
}

private fun resolveCursorIdleColor(style: GamepadCursorColorStyle): Color = when (style) {
    GamepadCursorColorStyle.CLASSIC_WHITE -> Color.White
    GamepadCursorColorStyle.BILI_PINK -> Color(0xFFFB7299)
    GamepadCursorColorStyle.CYAN -> Color(0xFF38BDF8)
    GamepadCursorColorStyle.GOLD -> Color(0xFFFACC15)
}

private fun resolveLegendHints(
    scene: GamepadScene,
    mode: GamepadControlMode,
    cursorVisible: Boolean,
    profile: GamepadControllerProfile,
    config: GamepadConfig,
): List<Pair<String, String>> {
    fun badge(slot: GamepadActionSlot): String? {
        val code = resolveBoundKeyCode(config, slot, profile)
        if (code == GamepadKeyCodes.UNBOUND) return null
        return formatGamepadKeyBadge(code, profile)
    }

    val (_, rtBadge) = formatTriggerPairLabels(profile)
    val confirm = badge(GamepadActionSlot.CONFIRM)
    val back = badge(GamepadActionSlot.BACK)
    val toggleMode = badge(GamepadActionSlot.TOGGLE_MODE)
    val toggleCursor = badge(GamepadActionSlot.TOGGLE_CURSOR)

    return buildList {
        when (scene) {
            GamepadScene.VIDEO_FULLSCREEN -> if (cursorVisible) {
                add("LS" to "光标")
                confirm?.let { add(it to "点击/拖") }
                back?.let { add(it to "退出") }
                add(rtBadge to "倍速")
                badge(GamepadActionSlot.TOGGLE_DANMAKU)?.let { add(it to "弹幕") }
                toggleCursor?.let { add(it to "隐光标") }
            } else {
                confirm?.let { add(it to "播/停") }
                back?.let { add(it to "退出") }
                val prev = badge(GamepadActionSlot.SEEK_BACKWARD)
                val next = badge(GamepadActionSlot.SEEK_FORWARD)
                if (prev != null && next != null) {
                    add("$prev/$next" to "±${config.seekStepSeconds}s")
                }
                add(rtBadge to "倍速")
                badge(GamepadActionSlot.TOGGLE_DANMAKU)?.let { add(it to "弹幕") }
                toggleCursor?.let { add(it to "光标") }
            }

            GamepadScene.VIDEO_DETAIL -> {
                add("LS" to if (mode == GamepadControlMode.CURSOR) "光标" else "焦点")
                add("RS" to "滚动")
                confirm?.let { add(it to "选择") }
                back?.let { add(it to "返回") }
                badge(GamepadActionSlot.TOGGLE_FULLSCREEN)?.let { add(it to "全屏/三连") }
                add(rtBadge to "倍速")
            }

            GamepadScene.BROWSING -> {
                add("LS" to if (mode == GamepadControlMode.CURSOR) "光标" else "焦点")
                add("RS" to "滚动")
                confirm?.let { add(it to "选择") }
                back?.let { add(it to "返回") }
                val prevTab = badge(GamepadActionSlot.PREV_TAB)
                val nextTab = badge(GamepadActionSlot.NEXT_TAB)
                if (prevTab != null && nextTab != null) {
                    add("$prevTab/$nextTab" to "切页")
                }
                badge(GamepadActionSlot.OPEN_SEARCH)?.let { add(it to "搜索") }
                toggleMode?.let { add(it to "切模式") }
            }
        }
    }
}
