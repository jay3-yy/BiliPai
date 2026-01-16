package com.android.purebilibili.core.util

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  iOS 风格 Spring 动画预设
 * 
 * 基于 iOS Human Interface Guidelines 的动画参数，
 * 提供统一的弹性动画效果，让交互更加自然流畅。
 */
object iOSSpringSpecs {
    
    /**
     * 按钮点击反馈动画
     * - 快速响应，轻微回弹
     * - 适用于 IconButton、ActionButton 等
     */
    val ButtonPress: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.6f,
        stiffness = 400f
    )
    
    /**
     * 页面切换动画
     * - 无回弹，自然停止
     * - 适用于导航过渡、页面滑入滑出
     */
    val PageTransition: SpringSpec<Float> = SpringSpec(
        dampingRatio = 1f,  // 临界阻尼，无回弹
        stiffness = Spring.StiffnessMediumLow
    )
    
    /**
     * 卡片展开动画
     * - 适度回弹，有活力感
     * - 适用于卡片详情展开、BottomSheet 弹出
     */
    val CardExpand: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.8f,
        stiffness = 300f
    )
    
    /**
     * 侧边栏/抽屉动画
     * - 轻微回弹，快速响应
     * - 适用于 Sidebar、Drawer 展开收起
     */
    val Drawer: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.7f,
        stiffness = 350f
    )
    
    /**
     * 列表项入场动画
     * - 中等回弹，Q弹效果
     * - 适用于 LazyColumn 卡片入场
     */
    val ListItem: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.65f,
        stiffness = 300f
    )
    
    /**
     * 刷新指示器动画
     * - 轻微回弹
     * - 适用于下拉刷新旋转动画
     */
    val RefreshIndicator: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.7f,
        stiffness = 300f
    )
    
    /**
     * 缩放动画通用参数
     * - 适用于 scale 变换的通用预设
     */
    val Scale: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 *  列表项进场动画 (Premium 非线性动画)
 * 
 * 特点：
 * - 交错延迟实现波浪效果
 * - 从下方滑入 + 缩放 + 淡入
 * - 非线性缓动曲线 (FastOutSlowIn)
 * - Q弹果冻回弹效果
 * 
 * @param index: 列表项的索引，用于计算延迟时间
 * @param key: 用于触发重置动画的键值 (通常传视频ID)
 * @param initialOffsetY: 初始 Y 偏移量
 * @param animationEnabled: 是否启用动画 (设置开关)
 */
fun Modifier.animateEnter(
    index: Int = 0,
    key: Any? = Unit,
    initialOffsetY: Float = 60f,  // 🚀 [优化] 减少位移距离
    animationEnabled: Boolean = true
): Modifier = composed {
    // 🚀 [优化] 如果动画被禁用，直接返回无动画效果
    if (!animationEnabled) {
        return@composed this
    }
    
    // 🚀 [优化] 检查是否从详情页返回，跳过动画
    if (CardPositionManager.isReturningFromDetail) {
        LaunchedEffect(Unit) {
            delay(100)
            CardPositionManager.clearReturning()
        }
        return@composed this
    }
    
    //  [修复] 检查是否正在切换分类，跳过动画避免收缩效果
    if (CardPositionManager.isSwitchingCategory) {
        LaunchedEffect(Unit) {
            delay(300)  // 等待分类切换完成
            CardPositionManager.isSwitchingCategory = false
        }
        return@composed this
    }
    
    // 🚀 [性能优化] 使用单一进度值驱动所有动画属性
    // 替代原来的 3 个 Animatable 对象，减少内存分配和协程开销
    var animationStarted by remember(key) { mutableStateOf(false) }
    
    // 计算交错延迟：每个卡片延迟 30ms，最多 200ms
    val delayMs = (index * 30).coerceAtMost(200)
    
    LaunchedEffect(key) {
        delay(delayMs.toLong())
        animationStarted = true
    }
    
    val progress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,    // 轻微过冲
            stiffness = 350f        // 适中的弹性
        ),
        label = "enterProgress"
    )
    
    this.graphicsLayer {
        alpha = progress
        translationY = initialOffsetY * (1f - progress)
        scaleX = 0.9f + 0.1f * progress
        scaleY = 0.9f + 0.1f * progress
    }
}

/**
 * 2. Q弹点击效果 (按压缩放)
 */
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.90f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BouncyScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

// =============================================================================
//  物理动画效果 - 符合真实物理规律的交互动画
// =============================================================================

/**
 *  重力下落动画 (Gravity Drop)
 * 
 * 模拟物体从高处落下并反弹的效果，适用于：
 * - 弹窗/对话框出现
 * - 卡片入场
 * - 删除确认动画
 * 
 * @param enabled 是否启用动画
 * @param initialOffsetY 初始下落高度 (负值表示从上方落下)
 * @param bounceCount 反弹次数
 */
fun Modifier.gravityDrop(
    enabled: Boolean = true,
    initialOffsetY: Float = -200f,
    bounceCount: Int = 2
): Modifier = composed {
    if (!enabled) return@composed this
    
    val offsetY = remember { Animatable(initialOffsetY) }
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // 淡入
        launch { alpha.animateTo(1f, tween(150)) }
        
        // 模拟重力下落 + 反弹
        // 使用递减的反弹高度模拟能量损耗
        var currentBounce = 0
        var bounceHeight = -initialOffsetY * 0.3f
        
        // 初始下落
        offsetY.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.5f,  // 弹性
                stiffness = 300f
            )
        )
        
        // 反弹循环
        while (currentBounce < bounceCount && bounceHeight > 5f) {
            offsetY.animateTo(
                targetValue = -bounceHeight,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            )
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 350f)
            )
            bounceHeight *= 0.5f  // 每次反弹高度减半
            currentBounce++
        }
    }
    
    this.graphicsLayer {
        translationY = offsetY.value
        this.alpha = alpha.value
    }
}

/**
 *  橡皮筋效果 (Rubber Band)
 * 
 * 类似 iOS 滚动到边界时的弹性拉伸效果，适用于：
 * - 边界滚动
 * - 下拉刷新
 * - 过度拖拽
 * 
 * @param dragOffset 当前拖拽偏移量
 * @param resistance 阻力系数 (0-1)，越大阻力越强
 */
fun Modifier.rubberBand(
    dragOffset: Float,
    resistance: Float = 0.55f
): Modifier = composed {
    // 使用对数函数创建自然的阻力递增效果
    val dampedOffset = remember(dragOffset, resistance) {
        if (dragOffset == 0f) 0f
        else {
            val sign = if (dragOffset > 0) 1f else -1f
            val absOffset = kotlin.math.abs(dragOffset)
            sign * (1f - resistance) * absOffset * (1f - kotlin.math.exp(-absOffset / 300f))
        }
    }
    
    this.graphicsLayer {
        translationY = dampedOffset
    }
}

/**
 *  钟摆摇摆动画 (Pendulum Swing)
 * 
 * 模拟钟摆的自然摇摆效果，适用于：
 * - 通知提醒
 * - 注意力引导
 * - 错误提示
 * 
 * @param trigger 触发摇摆的标识 (变化时触发)
 * @param initialAngle 初始摇摆角度
 */
fun Modifier.pendulumSwing(
    trigger: Any,
    initialAngle: Float = 15f
): Modifier = composed {
    val rotation = remember { Animatable(0f) }
    
    LaunchedEffect(trigger) {
        // 使用渐衰的摇摆模拟钟摆
        var angle = initialAngle
        var direction = 1f
        
        while (angle > 0.5f) {
            rotation.animateTo(
                targetValue = angle * direction,
                animationSpec = spring(
                    dampingRatio = 0.3f,  // 低阻尼产生持续摇摆
                    stiffness = 200f
                )
            )
            direction *= -1  // 反向
            angle *= 0.7f    // 衰减
        }
        
        // 归位
        rotation.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    
    this.graphicsLayer {
        rotationZ = rotation.value
    }
}

/**
 *  呼吸动画 (Breathing Effect)
 * 
 * 模拟生物呼吸的周期性缩放效果，适用于：
 * - 录制中状态
 * - 等待/加载提示
 * - 引导用户注意
 * 
 * @param enabled 是否启用动画
 * @param minScale 最小缩放
 * @param maxScale 最大缩放
 * @param durationMs 一个呼吸周期的时长
 */
fun Modifier.breathe(
    enabled: Boolean = true,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    durationMs: Int = 2000
): Modifier = composed {
    if (!enabled) return@composed this
    
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMs / 2,
                easing = FastOutSlowInEasing  // 自然的加速减速
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_alpha"
    )
    
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 *  3D 透视倾斜效果 (Perspective Tilt)
 * 
 * 根据按压位置产生 3D 倾斜效果，模拟真实卡片被按压的物理反馈，适用于：
 * - 卡片点击
 * - 按钮交互
 * 
 * @param pressOffset 按压点相对于中心的偏移 (Offset)
 * @param isPressed 是否处于按压状态
 * @param maxRotation 最大旋转角度
 */
fun Modifier.perspectiveTilt(
    pressOffset: Offset = Offset.Zero,
    isPressed: Boolean = false,
    maxRotation: Float = 8f
): Modifier = composed {
    val rotationX by animateFloatAsState(
        targetValue = if (isPressed) -pressOffset.y.coerceIn(-1f, 1f) * maxRotation else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tilt_rotationX"
    )
    
    val rotationY by animateFloatAsState(
        targetValue = if (isPressed) pressOffset.x.coerceIn(-1f, 1f) * maxRotation else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tilt_rotationY"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "tilt_scale"
    )
    
    this.graphicsLayer {
        this.rotationX = rotationX
        this.rotationY = rotationY
        scaleX = scale
        scaleY = scale
        // 增加透视感
        cameraDistance = 12f * density
    }
}

/**
 *  弹跳入场动画 (Bounce In)
 * 
 * 元素从下方弹入并带有过冲效果，适用于：
 * - 底栏图标切换
 * - 列表项入场
 * 
 * @param visible 是否可见
 * @param initialOffsetY 初始 Y 偏移
 */
fun Modifier.bounceIn(
    visible: Boolean,
    initialOffsetY: Float = 30f
): Modifier = composed {
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else initialOffsetY,
        animationSpec = spring(
            dampingRatio = 0.4f,  // 明显过冲
            stiffness = 350f
        ),
        label = "bounce_in_offset"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200),
        label = "bounce_in_alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "bounce_in_scale"
    )
    
    this.graphicsLayer {
        translationY = offsetY
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
    }
}