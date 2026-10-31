package com.android.purebilibili.core.ui.popup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.components.AppSingleChoicePopupPresenter
import com.android.purebilibili.core.ui.components.AppSingleChoicePopupRequest
import com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress
import com.android.purebilibili.core.ui.components.AppGlassMenuPresenter
import com.android.purebilibili.core.ui.components.AppGlassMenuRequest
import com.android.purebilibili.core.ui.components.AppGlassMenuPlacement
import com.android.purebilibili.core.ui.components.AppGlassMenuScope
import com.android.purebilibili.core.ui.components.LocalAppGlassMenuPresenter
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.LocalAppSingleChoicePopupPresenter
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** 液态玻璃弹窗与页面的安全边距。 */
private val PopupScreenMargin = 16.dp

/** 锚定菜单向上覆盖源控件的距离。 */
private val PopupAnchorOverlap = 8.dp

/** 玻璃弹窗的基础背景模糊，供同类浮层共用。 */
internal val AppGlassPopupBackdropBlur = 12.dp

/**
 * 锚定弹窗的默认最大宽度：与原生菜单一致按窗口宽度自适应，
 * 内容多宽就多宽，仅在超出窗口时钳制；调用方显式传入 maxWidth 时优先。
 */
private val PopupMinWidth = 160.dp

/** 居中弹窗的最大宽度（与 AppSingleChoiceDialog 的 420dp 策略一致）。 */
private val CenteredPopupMaxWidth = 420.dp

/** 居中弹窗最大占宿主高度比例（与 AppSingleChoiceDialog 的 0.8 一致）。 */
private const val CenteredPopupMaxHeightFraction = 0.8f

/** 菜单卡片圆角：卡片比条目圆角大出内边距一档，玻璃专属，不影响原生弹窗。 */
private val PopupCornerRadius = 24.dp

// 24dp 外圆角与 8dp 内缩对应 16dp 条目圆角；文字总起点仍为 20dp。
private val PopupItemInset = 8.dp
private val PopupItemCornerRadius = 16.dp
private val OptionStartPadding = 12.dp
private val OptionEndPadding = 12.dp
private val OptionVerticalPadding = 12.dp

/** 菜单玻璃的折射占整块玻璃调制的比例（低于 dock 壳，避免文字背景过度扭曲）。 */
private const val MenuLensScale = 0.5f

/** 一次弹窗形变的实时状态：阶段、时钟与几何输入（窗口坐标系，像素）。 */
@Stable
internal class PopupMorphState {
    var opening by mutableStateOf(true)
        private set
    var seconds by mutableFloatStateOf(0f)
        private set
    var hasSourceControl = false
        private set
    var elastic = PopupElasticMotion()
        private set
    var closeStartFrame: AppGlassPopupFrame? = null
        private set
    var openStartFrame: AppGlassPopupFrame? = null
        private set
    var previousFrame: AppGlassPopupFrame? = null
        private set
    var sampleSeconds = 1f / 120f
        private set
    var interruptedClose = false
        private set
    val openDuration: Float get() = if (openStartFrame == null) AppGlassPopupMotion.OpenSeconds else 0.25f
    var closeStartSeconds = AppGlassPopupMotion.OpenSeconds
        private set
    var session by mutableIntStateOf(0)
        private set
    // 完整展示的身份独立于反向动画；关闭中重开保留测量与内容节点。
    var presentation by mutableIntStateOf(0)
        private set

    /** 锚点值按钮的近似方形（行高为边长，贴行尾）。 */
    var anchor by mutableStateOf(Rect.Zero)
    var card by mutableStateOf(Rect.Zero)
    var safeBounds by mutableStateOf(Rect.Zero)

    fun advanceTo(elapsedSeconds: Float, expectedSession: Int = session): Boolean {
        // 等待下一帧期间可能已经关闭或反向；旧时钟不得推进新动画。
        if (expectedSession != session) return false
        seconds = elapsedSeconds.coerceAtLeast(0f)
        return true
    }

    fun beginOpen(sourceControl: Boolean) {
        presentation++
        hasSourceControl = sourceControl
        elastic = PopupElasticMotion()
        closeStartFrame = null
        openStartFrame = null
        previousFrame = null
        interruptedClose = false
        card = Rect.Zero
        anchor = Rect.Zero
        opening = true
        seconds = 0f
        session++
    }

    fun detachSource() { hasSourceControl = false }

    fun closeWithParent(frame: AppGlassPopupFrame, previous: AppGlassPopupFrame) {
        hasSourceControl = false
        closeStartFrame = frame
        previousFrame = previous
        sampleSeconds = seconds.coerceIn(0.0001f, 1f / 120f)
        closeStartSeconds = seconds
        interruptedClose = true
        opening = false
        seconds = 0f
        session++
    }

    fun reopen(frame: AppGlassPopupFrame, previous: AppGlassPopupFrame) {
        openStartFrame = frame
        previousFrame = previous
        sampleSeconds = seconds.coerceIn(0.0001f, 1f / 120f)
        elastic = PopupElasticMotion()
        opening = true
        seconds = 0f
        session++
    }

    fun beginClose(frame: AppGlassPopupFrame, previous: AppGlassPopupFrame) {
        if (opening) {
            closeStartFrame = frame
            previousFrame = previous
            sampleSeconds = seconds.coerceIn(0.0001f, 1f / 120f)
            interruptedClose = seconds < openDuration || openStartFrame != null
            closeStartSeconds = seconds
            opening = false
            seconds = 0f
            session++
        }
    }
}

/**
 * 页面与播放器的同窗玻璃菜单宿主。
 *
 * 玻璃卡片必须与页面内容同窗渲染（miuix-blur 的 Backdrop 图层不能跨平台窗口读取），
 * 页面内容只录制一次供屏幕与玻璃重放，弹窗是采样源之外的兄弟层。
 * 宿主仅在液态玻璃可用时装配；玻璃关闭时不提供 presenter，
 * 单选条目保持 Miuix 滚轮 / MD3 菜单 / 居中弹窗的原生路径不变。
 *
 * [bottomInset] 为覆盖在页面底部的导航栏高度，弹窗与手势拦截区都让出该区域。
 */
@Composable
internal fun AppGlassPopupHost(
    glassAvailable: Boolean,
    tuning: LiquidGlassTuning,
    bottomInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    // 结构不随玻璃开关切换：内容保持在同一组合位置，玻璃关闭时仅撤销 presenter。
    // 若按 glassAvailable 分支拆装内容树，共享的 SaveableStateHolder 会在新旧两份
    // SaveableStateProvider 交接帧里以同一键注册两次（Key was used multiple times）。
    AppRetainedDialogHost {
        AppGlassPopupContentHost(tuning, bottomInset, glassAvailable, content)
    }
}

@Composable
private fun AppGlassPopupContentHost(
    tuning: LiquidGlassTuning,
    bottomInset: Dp,
    glassAvailable: Boolean,
    content: @Composable () -> Unit,
) {
    val backgroundFocus = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current ||
        !com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val latestReduceMotion by rememberUpdatedState(reduceMotion)
    val activeElastic = remember { arrayOfNulls<PopupElasticMotion>(1) }
    val controller = remember(scope, haptic) {
        AppGlassChoicePopupController(
            onElasticTarget = { motion, pressed, pull ->
                if (activeElastic[0] !== motion) activeElastic[0]?.stop()
                activeElastic[0] = motion
                motion.retarget(scope, pressed, pull, reduced = latestReduceMotion)
            },
            onCancelElastic = { activeElastic[0]?.stop(); activeElastic[0] = null },
            onHoverChanged = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        )
    }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    val parentExiting = com.android.purebilibili.core.ui.LocalAppDialogExitRunning.current
    val latestParentExiting by rememberUpdatedState(parentExiting)
    val finishPopup = remember(controller, scope, backgroundFocus, inputModeManager) {
        { session: Int ->
            val action = controller.finish(session)
            scope.launch {
                // 动作在宿主作用域中提交，关闭通知自带会话校验；不留下可被新打开请求抢占的空帧。
                action?.invoke()
                withFrameNanos { }
                if (!latestParentExiting && controller.request == null && inputModeManager.inputMode == InputMode.Keyboard) {
                    backgroundFocus.requestFocus()
                }
            }
            Unit
        }
    }
    val windowHostRenderer = remember(tuning, glassAvailable) {
        if (!glassAvailable) null else object : com.android.purebilibili.core.ui.AppPopupWindowHostRenderer {
            @Composable
            override fun Render(content: @Composable () -> Unit) {
                CompositionLocalProvider(
                    com.android.purebilibili.core.ui.components.LocalAppGlassMenuBackdrop provides null,
                    com.android.purebilibili.core.ui.components.LocalAppGlassMenuSource provides null,
                    com.android.purebilibili.core.ui.components.LocalAppGlassMenuScope provides null,
                    com.android.purebilibili.core.ui.blur.LocalAppGlassContainer provides null,
                ) {
                    AppGlassPopupHost(glassAvailable = true, tuning = tuning, content = content)
                }
            }
        }
    }
    SideEffect { if (parentExiting) controller.dismissWithParent() }
    val hostView = LocalView.current
    val menuPresenter = if (!glassAvailable) null else remember(controller, hostView) {
        object : AppGlassMenuPresenter {
            override val ownerView = hostView
            override fun show(owner: Any, request: AppGlassMenuRequest) {
                if (latestParentExiting) request.carriedPress?.cancel() else controller.showMenu(owner, request)
            }
            override fun dismiss(owner: Any) { controller.dismissMenu(owner) }
        }
    }
    val presenter = if (!glassAvailable) null else remember(controller) {
        AppSingleChoicePopupPresenter { request, onSelect ->
            if (latestParentExiting) request.carriedPress?.cancel() else controller.show(request, onSelect)
        }
    }
    val request = controller.request
    val source = key(controller, controller.morph.presentation) { rememberChromeBackdropSource() }
    // 播放器背景只覆盖媒体视口；先铺完整页面，再叠加排除控件的视频层，
    // 让越过播放器边界的菜单继续采样详情、列表及窄窗口中的实际内容。
    val playerBackdrop = request?.menu?.backdrop
    val menuBackdrop = if (playerBackdrop != null) {
        com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop(
            source.backdrop,
            playerBackdrop,
        )
    } else source.backdrop
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (request != null) source.modifier else Modifier)
                .focusRequester(backgroundFocus)
                .focusRestorer()
                .focusGroup()
                .then(if (request != null) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            CompositionLocalProvider(
                com.android.purebilibili.core.ui.LocalAppPopupWindowHostRenderer provides windowHostRenderer,
                com.android.purebilibili.core.ui.components.LocalAppGlassMenuActive provides (request != null),
                LocalAppSingleChoicePopupPresenter provides presenter,
                LocalAppGlassMenuPresenter provides menuPresenter,
            ) {
                content()
            }
        }
        if (request != null) {
            // 即使关闭与再次打开合并在同一帧，也重新测量完整的新展示。
            // 同一展示中反向只更新动画 session，不丢失当前轮廓及滚动位置。
            key(controller, controller.morph.presentation) {
                AppGlassChoicePopupOverlay(
                    request = request,
                    tuning = tuning,
                    controller = controller,
                    backdrop = menuBackdrop,
                    backdropReady = source.isReady,
                    onFinished = finishPopup,
                    bottomInset = bottomInset,
                )
            }
        }
    }
}

/** 一次玻璃弹窗的显示状态；request 为 null 时不参与组合。 */
internal class AppGlassChoicePopupController(
    private val onElasticTarget: (PopupElasticMotion, Boolean, Offset) -> Unit,
    private val onHoverChanged: () -> Unit,
    private val onCancelElastic: () -> Unit = {},
) {
    var request by mutableStateOf<AppSingleChoicePopupRequest?>(null)
        private set
    val morph = PopupMorphState()
    var lightPressed by mutableStateOf(false)
        private set
    var lightPosition by mutableStateOf(Offset.Zero)
        private set
    fun updateLight(position: Offset, pressed: Boolean) {
        lightPosition = position
        lightPressed = pressed && morph.opening
    }
    var pressedIndex by mutableStateOf<Int?>(null)
        private set
    private val rows = mutableMapOf<Int, LayoutCoordinates>()
    private data class MenuRow(val coordinates: LayoutCoordinates, val enabled: Boolean,
        val dismissOnClick: Boolean, val action: () -> Unit)
    private val menuRows = linkedMapOf<Any, MenuRow>()
    private var menuOwner: Any? = null
    private var latestMenu: AppGlassMenuRequest? = null
    private var pressedMenuItem by mutableStateOf<Any?>(null)
    val menuScope = AppGlassMenuScope(
        pressedItem = { pressedMenuItem },
        register = { key, coordinates, enabled, dismiss, action ->
            menuRows[key] = MenuRow(coordinates, enabled, dismiss, action)
        },
        unregister = { menuRows.remove(it) },
        dispatch = { action, dismiss ->
            if (morph.opening) {
                if (dismiss) { pendingSelection = action; requestDismiss() } else { cancelPress(); action() }
            }
        },
    )
    private var pressActive = false
    private var pressCanSelect = true
    private var boundaryPull = false
    private var elasticPressAllowed = false
    private var allowPressGrowth = true
    private var pressOrigin = Offset.Zero
    private var pressSlop = 8f
    private var dragFeedbackEnabled = true
    private var pointer = Offset.Zero
    private var density = 1f
    private var radius = 24f
    private var lastElasticTarget: Triple<Boolean, Offset, Boolean>? = null
    private var onSelect: ((Any?) -> Unit)? = null
    private var pendingSelection: (() -> Unit)? = null
    private var parentExitHandled = false

    fun configure(density: Float, radius: Float, touchSlop: Float = 8f * density, dragEnabled: Boolean = true) {
        this.density = density
        this.radius = radius
        pressSlop = touchSlop
        dragFeedbackEnabled = dragEnabled
    }

    fun restoreSource() { request?.onSourceVisibilityChange?.invoke(true) }

    fun dispose() {
        onCancelElastic()
        request?.carriedPress?.attach(null)
        pressActive = false
        restoreSource()
    }

    fun showMenu(owner: Any, menu: AppGlassMenuRequest) {
        if (request != null) {
            if (menuOwner === owner) {
                latestMenu = menu
                // 同一入口的重复 show 不关闭现有菜单，也不重新开始展开弹簧。
                if (!morph.opening && pendingSelection == null) resumeOpen()
            } else { menu.carriedPress?.cancel(); menu.onDismissRequest() }
            return
        }
        menuOwner = owner
        latestMenu = menu
        show(AppSingleChoicePopupRequest(title = menu.title, options = emptyList(), selectedValue = null,
            anchorBoundsInWindow = menu.anchor(), centered = menu.placement == AppGlassMenuPlacement.CENTER,
            controlBoundsInWindow = (menu.sourceBounds() ?: menu.anchor()).takeIf { menu.sourceLayer != null },
            sourceLayer = menu.sourceLayer,
            carriedPress = menu.carriedPress,
            onSourceVisibilityChange = menu.onSourceVisibilityChange,
            menu = menu), {})
    }

    fun dismissMenu(owner: Any) {
        if (menuOwner === owner && request != null) {
            pendingSelection = null
            requestDismiss()
        }
    }

    fun show(request: AppSingleChoicePopupRequest, onSelect: (Any?) -> Unit) {
        val active = this.request
        if (active != null) {
            val sameSource = request.sourceLayer != null && request.sourceLayer === active.sourceLayer
            request.carriedPress?.cancel()
            if (sameSource && menuOwner == null && !morph.opening && pendingSelection == null) {
                this.onSelect = onSelect
                resumeOpen()
            }
            return
        }
        this.onSelect = onSelect
        parentExitHandled = false
        pendingSelection = null
        rows.clear()
        menuRows.clear()
        pressedMenuItem = null
        pressedIndex = null
        pressActive = false
        lastElasticTarget = null
        // 居中交互面板可显式携带入口快照，位置仍居中，只让开合形变衔接真实入口。
        val sourceControl = (!request.centered || request.menu?.interactiveContent == true) &&
            request.sourceLayer != null && request.controlBoundsInWindow != null
        morph.beginOpen(sourceControl)
        this.request = request
        if (sourceControl) request.onSourceVisibilityChange(false)
        request.carriedPress?.attach(object : AppSingleChoicePopupPress.Listener {
            override fun onMove(positionInWindow: Offset) {
                if (pressActive) movePress(positionInWindow) else beginPress(positionInWindow, feedback = true, carriedDrag = true)
            }
            override fun onRelease(positionInWindow: Offset) { releasePress(positionInWindow) }
            override fun onCancel() { cancelPress() }
        })
    }

    fun registerRow(index: Int, coordinates: LayoutCoordinates) {
        rows[index] = coordinates
    }

    private fun indexAt(position: Offset): Int? {
        if (!morph.frame(radius, density).rect.contains(position)) return null
        return rows.entries.firstOrNull { (_, coordinates) ->
            coordinates.isAttached && coordinates.boundsInWindow().let { !it.isEmpty && it.contains(position) }
        }?.key
    }

    private fun menuRowAt(position: Offset): Any? {
        if (!morph.frame(radius, density).rect.contains(position)) return null
        return menuRows.entries.firstOrNull { (_, row) -> row.enabled && row.coordinates.isAttached &&
            row.coordinates.boundsInWindow().let { !it.isEmpty && it.contains(position) } }?.key
    }

    private fun updateHover(feedback: Boolean) {
        if (request?.menu != null) {
            val next = if (pressActive && pressCanSelect) menuRowAt(pointer) else null
            if (next !== pressedMenuItem) {
                pressedMenuItem = next
                if (feedback && next != null) onHoverChanged()
            }
            return
        }
        val next = if (pressActive && pressCanSelect) indexAt(pointer) else null
        if (next != pressedIndex) {
            pressedIndex = next
            if (feedback && next != null) onHoverChanged()
        }
    }

    private fun driveElastic() {
        if (request == null || !morph.opening) return
        val ready = morph.seconds >= morph.openDuration
        val pressed = pressActive && elasticPressAllowed
        val pull = if (dragFeedbackEnabled && pressActive && (ready || elasticPressAllowed)) {
            if (boundaryPull) {
                popupRubberBandPull(pointer - pressOrigin, morph.card.minDimension)
            } else {
                popupMenuDragPull(pointer, pressOrigin, morph.card, followInside = pressCanSelect)
            }
        } else Offset.Zero
        morph.elastic.isTracking = pressActive
        val target = Triple(pressed, pull, pressActive)
        if (target == lastElasticTarget) return
        lastElasticTarget = target
        onElasticTarget(morph.elastic, pressed, pull)
    }

    fun beginPress(position: Offset, feedback: Boolean = false, allowGrowth: Boolean = true, carriedDrag: Boolean = false): Boolean {
        if (!morph.opening || pressActive) return false
        pressActive = true
        pressCanSelect = true
        boundaryPull = false
        // 静止按住不在落定后补放大；真实拖拽立即启动，并由展开混合曲线平滑接入。
        allowPressGrowth = allowGrowth
        pressOrigin = if (carriedDrag && !morph.card.isEmpty) Offset(
            position.x.coerceIn(morph.card.left, morph.card.right),
            position.y.coerceIn(morph.card.top, morph.card.bottom),
        ) else position
        elasticPressAllowed = allowGrowth && (carriedDrag || morph.seconds >= morph.openDuration)
        pointer = position
        updateHover(feedback = feedback)
        driveElastic()
        return true
    }

    fun movePress(position: Offset, allowSelection: Boolean = true, allowDeformation: Boolean = true, fromScrollBoundary: Boolean = false) {
        if (!pressActive || !morph.opening) return
        // 滚动接管后仍观察位移驱动整块玻璃，但本轮松手不能选中菜单项。
        pressCanSelect = pressCanSelect && allowSelection
        boundaryPull = fromScrollBoundary
        if (!allowDeformation) {
            elasticPressAllowed = false
        } else {
            allowPressGrowth = true
            if ((position - pressOrigin).getDistance() > pressSlop) elasticPressAllowed = true
        }
        pointer = if (allowDeformation) position else pressOrigin
        updateHover(feedback = true)
        driveElastic()
    }

    fun refreshPress() {
        if (pressActive) movePress(pointer, fromScrollBoundary = boundaryPull)
    }

    fun cancelPress() {
        pressActive = false
        elasticPressAllowed = false
        pressedIndex = null
        pressedMenuItem = null
        driveElastic()
    }

    fun releasePress(position: Offset) {
        if (!pressActive || !morph.opening) return
        if (!pressCanSelect) { cancelPress(); return }
        if (request?.menu != null) {
            val row = menuRowAt(position)?.let(menuRows::get)
            if (row != null) menuScope.dispatch(row.action, row.dismissOnClick) else cancelPress()
            return
        }
        val option = indexAt(position)?.let { request?.options?.getOrNull(it) }
        if (option != null) select(option.value) else cancelPress()
    }

    private fun resumeOpen() {
        val frame = morph.frame(radius, density)
        val previous = morph.frameAt((morph.seconds - 1f / 120f).coerceAtLeast(0f), radius, density)
        onCancelElastic()
        morph.reopen(frame, previous)
        lastElasticTarget = null
    }

    fun requestDismiss() {
        if (request == null || !morph.opening) return
        val frame = morph.frame(radius, density)
        val previous = morph.frameAt((morph.seconds - 1f / 120f).coerceAtLeast(0f), radius, density)
        val active = request
        val sourceAlive = active?.sourceLayer?.isReleased == false &&
            (active?.menu?.sourceAvailable?.invoke() ?: true)
        if (morph.hasSourceControl && !sourceAlive) morph.detachSource()
        onCancelElastic()
        morph.beginClose(frame, previous)
        request?.carriedPress?.attach(null)
        pressActive = false
        pressedIndex = null
        pressedMenuItem = null
        lastElasticTarget = null
    }

    fun dismissWithParent() {
        if (request == null || parentExitHandled) return
        parentExitHandled = true
        pendingSelection = null
        onSelect = null
        val frame = morph.frame(radius, density)
        val previous = morph.frameAt((morph.seconds - 1f / 120f).coerceAtLeast(0f), radius, density)
        onCancelElastic()
        request?.carriedPress?.attach(null)
        pressActive = false
        pressedIndex = null
        pressedMenuItem = null
        lastElasticTarget = null
        morph.closeWithParent(frame, previous)
    }

    /** 设置变更等待回收结束；触摸与语义点击共用单次分发入口。 */
    fun select(value: Any?) {
        if (!morph.opening) return
        val callback = onSelect
        onSelect = null
        pendingSelection = { callback?.invoke(value) }
        requestDismiss()
    }

    fun finish(expectedSession: Int = morph.session): (() -> Unit)? {
        if (request == null || morph.opening || expectedSession != morph.session) return null
        onCancelElastic()
        val action = pendingSelection
        val menu = latestMenu ?: request?.menu
        latestMenu = null
        pendingSelection = null
        onSelect = null
        request?.carriedPress?.attach(null)
        restoreSource()
        request = null
        menuOwner = null
        rows.clear()
        menuRows.clear()
        return if (parentExitHandled) null else if (menu == null) action else ({ action?.invoke(); menu.onDismissRequest(); Unit })
    }
}

@Composable
private fun AppGlassChoicePopupOverlay(
    request: AppSingleChoicePopupRequest,
    tuning: LiquidGlassTuning,
    controller: AppGlassChoicePopupController,
    backdrop: Backdrop,
    backdropReady: Boolean,
    onFinished: (Int) -> Unit,
    bottomInset: Dp,
) {
    val morph = controller.morph
    val menuFocus = remember(request) { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(request, inputModeManager.inputMode) {
        if (inputModeManager.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            menuFocus.requestFocus()
        }
    }

    BackHandler { controller.requestDismiss() }
    val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current ||
        !com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val latestBackdropReady by rememberUpdatedState(backdropReady)
    val latestMotionReduced by rememberUpdatedState(reduceMotion)
    LaunchedEffect(controller, request, morph.session) {
        val session = morph.session
        snapshotFlow { latestBackdropReady }.first { it }
        snapshotFlow { morph.card }.first { !it.isEmpty }
        if (session != morph.session || controller.request !== request) return@LaunchedEffect
        val durationScale = if (latestMotionReduced) 0f else currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        val duration = if (morph.opening) morph.openDuration else
            AppGlassPopupMotion.closeDuration(morph.hasSourceControl)
        if (durationScale > 0f) {
            val start = withFrameNanos { it }
            while (true) {
                val elapsed = (withFrameNanos { it } - start) / (1_000_000_000f * durationScale)
                if (controller.request !== request || !morph.advanceTo(
                        if (latestMotionReduced) duration else elapsed.coerceAtMost(duration), session,
                    )
                ) return@LaunchedEffect
                if (elapsed >= duration || latestMotionReduced) break
            }
        } else if (!morph.advanceTo(duration, session)) return@LaunchedEffect
        if (session == morph.session && controller.request === request) {
            if (!morph.opening) onFinished(session) else controller.refreshPress()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomInset)
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .focusRequester(menuFocus)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                    controller.requestDismiss()
                    true
                } else false
            }
            .semantics { if (request.title.isNotBlank()) paneTitle = request.title; isTraversalGroup = true }
            // 弹窗打开期间拦截页面手势：点按空白处关闭，拖拽不透传给列表滚动。
            .pointerInput(Unit) { detectTapGestures { controller.requestDismiss() } },
    ) {
        val density = LocalDensity.current
        val margins = PopupPlacementMargins(
            screenMarginPx = with(density) { PopupScreenMargin.roundToPx() },
            anchorOverlapPx = with(density) { PopupAnchorOverlap.roundToPx() },
            anchorMinWidthPx = with(density) { (request.menu?.minWidth ?: PopupMinWidth).roundToPx() },
            anchorMaxWidthPx = request.menu?.maxWidth?.let { with(density) { it.roundToPx() } },
            centeredMaxWidthPx = with(density) { (request.menu?.maxWidth ?: CenteredPopupMaxWidth).roundToPx() },
        )
        var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

        // 优先使用真实行尾控件；旧调用者未提供时才按行高估算，原生菜单不走此分支。
        val menu = request.menu
        val anchorRow = menu?.anchor?.invoke() ?: request.anchorBoundsInWindow
        val sourceBounds = menu?.sourceBounds?.invoke() ?: request.controlBoundsInWindow
        val capturedPanelSource = sourceBounds?.takeIf { menu?.interactiveContent == true && menu.sourceLayer != null }
        val anchorInWindow = capturedPanelSource ?: if (request.centered || (menu != null && menu.placement != AppGlassMenuPlacement.ANCHOR_END)) Rect.Zero else sourceBounds ?: anchorRow?.takeIf { menu != null } ?: anchorRow?.let { row ->
            val side = min(row.height, row.width)
            Rect(
                left = row.right - side,
                top = row.center.y - side / 2f,
                right = row.right,
                bottom = row.center.y + side / 2f,
            )
        } ?: Rect.Zero
        androidx.compose.runtime.SideEffect { morph.anchor = anchorInWindow }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    overlayOrigin = it.positionInWindow()
                    val inset = margins.screenMarginPx.toFloat()
                    morph.safeBounds = Rect(
                        overlayOrigin.x + inset,
                        overlayOrigin.y + inset,
                        overlayOrigin.x + (it.size.width - inset).coerceAtLeast(inset),
                        overlayOrigin.y + (it.size.height - inset).coerceAtLeast(inset),
                    )
                },
        ) {
            AppGlassChoicePopupPlacement(
                anchor = (if (menu != null) anchorRow else sourceBounds ?: anchorRow)?.let { row ->
                    Rect(
                        left = row.left - overlayOrigin.x,
                        top = row.top - overlayOrigin.y,
                        right = row.right - overlayOrigin.x,
                        bottom = row.bottom - overlayOrigin.y,
                    )
                },
                centered = request.centered,
                placement = request.menu?.placement ?: AppGlassMenuPlacement.ANCHOR_END,
                requestedMaxHeight = request.menu?.maxHeight?.let { with(density) { it.roundToPx() } },
                useAvailableHeight = request.menu?.useAvailableHeight == true,
                margins = margins,
            ) {
                AppGlassChoicePopupCard(
                    request = request,
                    backdrop = backdrop,
                    tuning = tuning,
                    morph = morph,
                    controller = controller,
                )
            }
        }
    }
}

/** 卡片布局的定位输入（像素）；[anchorMaxWidthPx] 为调用方显式限制，null 表示按窗口宽度自适应。 */
@Immutable
private data class PopupPlacementMargins(
    val screenMarginPx: Int,
    val anchorOverlapPx: Int,
    val anchorMinWidthPx: Int,
    val anchorMaxWidthPx: Int?,
    val centeredMaxWidthPx: Int,
)

/**
 * 菜单右侧对齐源控件，从源控件上方少量重叠展开；只在越界时钳制，居中模式保持居中。
 */
@Composable
private fun AppGlassChoicePopupPlacement(
    anchor: Rect?,
    centered: Boolean,
    placement: AppGlassMenuPlacement,
    requestedMaxHeight: Int?,
    useAvailableHeight: Boolean = false,
    margins: PopupPlacementMargins,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val margin = margins.screenMarginPx
        val maxWidth = if (centered || anchor == null) {
            margins.centeredMaxWidthPx.coerceAtMost(constraints.maxWidth - margin * 2)
        } else {
            // 与原生菜单一致：未显式限制时按内容自适应，仅受窗口宽度钳制。
            (margins.anchorMaxWidthPx ?: constraints.maxWidth - margin * 2)
                .coerceAtMost(constraints.maxWidth - margin * 2)
        }
        val maxHeight = if (useAvailableHeight) {
            constraints.maxHeight - margin * 2
        } else if (centered || anchor == null) {
            (constraints.maxHeight * CenteredPopupMaxHeightFraction).toInt()
        } else {
            // 长菜单保留上下形变空间，正文在受限视口内滚动。
            constraints.maxHeight - maxOf(margin, (constraints.maxHeight * 0.10f).toInt()) * 2
        }
        val placeable = measurables.first().measure(
            Constraints(
                minWidth = if (centered || anchor == null) 0 else margins.anchorMinWidthPx.coerceAtMost(maxWidth.coerceAtLeast(0)),
                maxWidth = maxWidth.coerceAtLeast(0),
                minHeight = 0,
                maxHeight = minOf(maxHeight, requestedMaxHeight ?: maxHeight).coerceAtLeast(0),
            ),
        )
        val width = placeable.width
        val height = placeable.height
        val boundsWidth = constraints.maxWidth
        val boundsHeight = constraints.maxHeight
        val position = resolveAppGlassPopupPosition(
            anchor = anchor,
            centered = centered,
            popupSize = IntSize(width, height),
            hostSize = IntSize(boundsWidth, boundsHeight),
            margin = margin,
            overlap = margins.anchorOverlapPx,
            placement = placement,
            verticalMargin = if (height > boundsHeight * 0.65f) {
                maxOf(margin, (boundsHeight * 0.10f).toInt())
            } else margin,
        )
        layout(boundsWidth, boundsHeight) { placeable.place(position.x, position.y) }
    }
}

private fun PopupMorphState.frame(radius: Float, density: Float): AppGlassPopupFrame = frameAt(seconds, radius, density)

private fun PopupMorphState.frameAt(time: Float, radius: Float, density: Float): AppGlassPopupFrame {
    val start = openStartFrame?.takeUnless { it.rect.isEmpty }
    val close = closeStartFrame?.takeUnless { it.rect.isEmpty }
    val base = when {
        opening && start != null -> AppGlassPopupMotion.retarget(time, openDuration, start,
            previousFrame ?: start, sampleSeconds, AppGlassPopupFrame(card, radius, 1f, 1f, 0f))
        opening -> AppGlassPopupMotion.opening(time, card, anchor, radius, hasSourceControl, density)
        interruptedClose && close != null -> {
            val duration = AppGlassPopupMotion.closeDuration(hasSourceControl)
            val target = AppGlassPopupMotion.closing(duration, closeStartSeconds, card, anchor, radius,
                hasSourceControl, density, close, returnToControl = hasSourceControl)
            AppGlassPopupMotion.retarget(time, duration, close, previousFrame ?: close, sampleSeconds, target)
        }
        else -> AppGlassPopupMotion.closing(time, closeStartSeconds, card, anchor, radius,
            hasSourceControl, density, close, returnToControl = hasSourceControl)
    }
    val valid = base.copy(radius = base.radius.coerceIn(0f, maxOf(0f, base.rect.minDimension / 2f)))
    return if (opening) fitPopupElasticFrame(
        elastic.frame(valid, density, popupDragBlend(time, openDuration)), safeBounds,
    ) else valid
}

/** 正文与玻璃共用按压和每轴拖拽缩放，光学层保留固定的窗口空间采样。 */
private fun GraphicsLayerScope.applyPopupContentFrame(morph: PopupMorphState, radius: Float, density: Float) {
    val card = morph.card
    if (card.isEmpty) {
        alpha = 0f
        return
    }
    val frame = morph.frame(radius, density)
    val scale = frame.contentScale
    scaleX = scale * frame.contentStretchX
    scaleY = scale * frame.contentStretchY
    translationX = frame.rect.center.x - card.center.x
    translationY = frame.rect.center.y - card.center.y
    transformOrigin = TransformOrigin.Center
    alpha = frame.contentAlpha
}

private fun Modifier.popupSurfaceEnvelope(morph: PopupMorphState, density: Float): Modifier = layout { measurable, constraints ->
    val card = morph.card
    val bounds = if (card.isEmpty) Rect(Offset.Zero, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
        else glassPopupSurfaceBounds(card, morph.anchor, density)
    val child = measurable.measure(Constraints.fixed(
        kotlin.math.ceil(bounds.width).toInt().coerceAtLeast(1),
        kotlin.math.ceil(bounds.height).toInt().coerceAtLeast(1),
    ))
    layout(constraints.maxWidth, constraints.maxHeight) {
        child.place(if (card.isEmpty) 0 else (bounds.left - card.left).roundToInt(),
            if (card.isEmpty) 0 else (bounds.top - card.top).roundToInt())
    }
}

private class PopupSurfaceShape(private val outline: Outline) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = outline
}

/** 轮廓在消费者的未缩放坐标中计算；最终玻璃与文字共用同一个窗口空间轮廓。 */
private data class PopupFrameShape(
    private val frame: AppGlassPopupFrame,
    private val card: Rect,
    private val content: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (card.isEmpty) return Outline.Rectangle(Rect(Offset.Zero, size))
        val (rect, radius) = glassPopupLocalOutline(frame, card, size, content)
        return Outline.Rounded(RoundRect(rect, if (content) {
            CornerRadius(radius / frame.contentStretchX, radius / frame.contentStretchY)
        } else CornerRadius(radius)))
    }
}

/** 只记录真实布局坐标；滚动位移还原成内容坐标，避免滚动时反复改变视口高度。 */
private class WholeMenuItemViewport {
    private var viewport: LayoutCoordinates? = null
    private val rows = linkedMapOf<Any, LayoutCoordinates>()
    var itemBottoms by mutableStateOf<List<Int>>(emptyList())
        private set

    fun register(key: Any, coordinates: LayoutCoordinates, scrollOffset: Int) {
        rows[key] = coordinates
        refresh(scrollOffset)
    }

    fun unregister(key: Any, scrollOffset: Int) {
        rows.remove(key)
        refresh(scrollOffset)
    }

    fun updateViewport(coordinates: LayoutCoordinates, scrollOffset: Int) {
        viewport = coordinates
        refresh(scrollOffset)
    }

    private fun refresh(scrollOffset: Int) {
        val parent = viewport?.takeIf { it.isAttached } ?: return
        itemBottoms = rows.values.filter { it.isAttached }.map { child ->
            (parent.localPositionOf(child, Offset.Zero).y + child.size.height + scrollOffset).roundToInt()
        }.distinct().sorted()
    }
}

@Composable
private fun AppGlassChoicePopupCard(
    request: AppSingleChoicePopupRequest,
    backdrop: Backdrop,
    tuning: LiquidGlassTuning,
    morph: PopupMorphState,
    controller: AppGlassChoicePopupController,
    modifier: Modifier = Modifier,
) {
    val isDark = isAppInDarkTheme()
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { PopupCornerRadius.toPx() }
    val densityScale = density.density
    val outlineCache = remember { PopupSurfaceOutlineCache() }
    // 源控件与正文独立缓存；半径变化仍逐帧更新，不量化动画或更改采样边界。
    val sourceBlurCache = remember(request) { BlurRenderEffectCache() }
    val contentBlurCache = remember(request) { BlurRenderEffectCache() }
    val ownScrollState = remember(request) { androidx.compose.foundation.ScrollState(0) }
    val scrollState = request.menu?.scrollState ?: ownScrollState
    val wholeItemViewport = remember(request) { WholeMenuItemViewport() }
    val fitWholeItems = request.menu?.fitWholeItems == true && request.menu?.interactiveContent != true
    val itemBottomPadding = with(density) { PopupItemInset.roundToPx() }
    // 只记录玻璃底层，排除正文与子控件，供内嵌标签栏采样，避免透出清晰的原始视频。
    val interactivePanel = request.menu?.interactiveContent == true
    val panelSurfaceSource = if (interactivePanel) rememberChromeBackdropSource() else null
    val contentBackdrop = panelSurfaceSource?.takeIf { it.isReady }?.backdrop ?: backdrop
    val contentMenuScope = remember(controller, contentBackdrop, wholeItemViewport, fitWholeItems, scrollState) {
        AppGlassMenuScope(
            pressedItem = controller.menuScope.pressedItem,
            register = { key, coordinates, enabled, dismiss, action ->
                controller.menuScope.register(key, coordinates, enabled, dismiss, action)
                if (fitWholeItems) wholeItemViewport.register(key, coordinates, scrollState.value)
            },
            unregister = { key ->
                controller.menuScope.unregister(key)
                if (fitWholeItems) wholeItemViewport.unregister(key, scrollState.value)
            },
            dispatch = controller.menuScope.dispatch,
            backdrop = contentBackdrop,
            fitWholeItems = fitWholeItems,
        )
    }
    var selectedItemBounds by remember(request) { mutableStateOf<Rect?>(null) }
    LaunchedEffect(request) {
        if (request.menu == null && request.options.any { it.value == request.selectedValue }) {
            val (_, bounds) = snapshotFlow { scrollState.viewportSize to selectedItemBounds }
                .first { (viewport, bounds) -> viewport > 0 && bounds != null }
            val item = bounds ?: return@LaunchedEffect
            scrollState.scrollTo(resolveGlassMenuInitialScroll(item, scrollState.viewportSize, scrollState.maxValue))
        }
    }
    val touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
    val dragEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.globalDragFeedbackEnabled
    SideEffect { controller.configure(densityScale, cornerRadiusPx, touchSlop, dragEnabled) }
    val materialLayer: GraphicsLayerScope.() -> Unit = remember(morph, cornerRadiusPx, densityScale) {
        { alpha = if (morph.card.isEmpty) 0f else morph.frame(cornerRadiusPx, densityScale).surfaceAlpha }
    }
    val lowBlurBudget = isLowBlurBudgetForced()
    val popupPalette = rememberGlassPopupPalette()
    val themeFrosted = popupPalette == com.android.purebilibili.core.store.home.GlassPopupPalette.THEME_FROSTED
    val paletteColors = androidx.compose.material3.MaterialTheme.colorScheme

    val lensIntensity = if (lowBlurBudget) 0f else (tuning.contentDistortionScale * MenuLensScale).coerceIn(0f, 1.8f)
    val shouldDrawLens = lensIntensity > 0.001f
    // 原有配色复用 Dock 底色；主题磨砂仅在玻璃弹窗内选择，原生组件不读取此选项。
    // 滑动触发预算降级时仍保留 backdrop 磨砂，不将玻璃切换为不透明底板。
    // 降级只减少采样半径并停用折射；配色与透明度在同一展示中保持一致。
    val blurRadiusPx = with(density) {
        (if (lowBlurBudget) 6.dp else if (interactivePanel && !themeFrosted) 24.dp else AppGlassPopupBackdropBlur).toPx()
    }
    val refractionHeightPx = with(density) { (tuning.refractionHeight * MenuLensScale).dp.toPx() }
    val refractionAmountPx = with(density) { (tuning.refractionAmount * MenuLensScale).dp.toPx() }
    val surfaceColor = if (themeFrosted) {
        resolveThemeFrostedPopupColor(isDark, paletteColors)
    } else resolveAppGlassSurfaceColor(
        darkTheme = isDark,
        lowBlurBudget = false,
        interactivePanel = interactivePanel,
        mediaBackdrop = request.menu?.backdrop != null,
        tuning = tuning,
    )
    val effects = remember(blurRadiusPx, refractionHeightPx, refractionAmountPx,
        shouldDrawLens, isDark, interactivePanel, themeFrosted, morph, cornerRadiusPx, densityScale) {
        val block: BackdropEffectScope.() -> Unit = {
            val frame = morph.frame(cornerRadiusPx, densityScale)
            val lensAmount = refractionAmountPx * (0.25f + 0.75f * frame.materialProgress)
            // 联合轮廓的折射边距先于模糊确定，复用原先二次求值后的最终采样范围。
            if (shouldDrawLens && !morph.card.isEmpty && !frame.rect.isEmpty &&
                refractionHeightPx > 0f && lensAmount > 0f
            ) {
                padding = maxOf(padding, lensAmount)
            }
            val frost = blurRadiusPx * (0.21f + 0.79f * frame.materialProgress)
            blur(frost, frost)
            colorControls(brightness = 0f,
                saturation = if (interactivePanel && !themeFrosted) 1f else if (isDark) 1.75f else 2f)
            if (shouldDrawLens && !morph.card.isEmpty) popupSurfaceLens(
                frame = frame,
                origin = glassPopupSurfaceBounds(morph.card, morph.anchor, densityScale).topLeft,
                height = refractionHeightPx,
                amount = lensAmount,
            )
        }
        block
    }
    val onDrawSurface = remember(surfaceColor) {
        val block: DrawScope.() -> Unit = { drawRect(surfaceColor) }
        block
    }
    val onDrawRim = remember(morph, cornerRadiusPx, isDark, densityScale) {
        val block: DrawScope.() -> Unit = {
            val frame = morph.frame(cornerRadiusPx, densityScale)
            val origin = glassPopupSurfaceBounds(morph.card, morph.anchor, densityScale).topLeft
            val outline = outlineCache.outline(frame, origin, densityScale)
            drawOutline(outline, (if (isDark) Color.White else Color.Black)
                .copy(alpha = if (isDark) 0.12f else 0.14f), style = Stroke(0.7.dp.toPx()))

        }
        block
    }

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                // 仅在布局阶段读取行边界；视口与滚动范围一起收紧，不靠绘制裁掉半行。
                val height = if (fitWholeItems) resolveWholeMenuViewportHeight(
                    constraints.maxHeight, wholeItemViewport.itemBottoms, itemBottomPadding,
                ) else constraints.maxHeight
                val placeable = measurable.measure(constraints.copy(
                    minHeight = constraints.minHeight.coerceAtMost(height), maxHeight = height,
                ))
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .onGloballyPositioned { coordinates ->
                // 本层不挂动画矩阵，不能将缩放后的包围盒重新写回动画终点。
                val origin = coordinates.positionInWindow()
                morph.card = Rect(origin, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            }
            .pointerInput(controller, touchSlop) {
                // 整面高光只在真实拖动越过手势阈值后出现，点按与静止按住均不点亮。
                // 独立旁观，不消费列表滚动或子控件的点击。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var dragging = false
                    controller.updateLight(morph.card.topLeft + down.position, false)
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || event.changes.count { it.pressed } > 1) break
                            dragging = dragging || (change.position - down.position).getDistance() > touchSlop
                            controller.updateLight(morph.card.topLeft + change.position, dragging)
                        }
                    } finally {
                        controller.updateLight(controller.lightPosition, false)
                    }
                }
            }
            .pointerInput(controller, request.menu?.interactiveContent) {
                if (request.menu?.interactiveContent == true) {
                    // 面板子控件先处理 Main 事件；仅滚动边界未消费的位移驱动整块形变。
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                        down.consume()
                        val origin = morph.card.center
                        var previousScroll = scrollState.value
                        var overscroll = 0f
                        var elasticActive = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed || event.changes.count { it.pressed } > 1) break
                                val drag = change.position - down.position
                                // 横向已消费的手势属于滑块/分区，优先保持精确调节。
                                if (kotlin.math.abs(drag.x) > touchSlop &&
                                    kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)) break
                                val nextScroll = scrollState.value
                                val remaining = popupPanelOverscrollDelta(
                                    fingerDelta = change.position.y - change.previousPosition.y,
                                    scrollBefore = previousScroll,
                                    scrollAfter = nextScroll,
                                    maxScroll = scrollState.maxValue,
                                )
                                previousScroll = nextScroll
                                overscroll = if (remaining == 0f) 0f else overscroll + remaining
                                if (kotlin.math.abs(overscroll) <= touchSlop) {
                                    if (elasticActive) controller.cancelPress()
                                    elasticActive = false
                                    continue
                                }
                                if (!elasticActive) elasticActive = controller.beginPress(origin)
                                if (elasticActive) {
                                    controller.movePress(origin + Offset(0f, overscroll), allowSelection = false, fromScrollBoundary = true)
                                }
                            }
                        } finally {
                            controller.cancelPress()
                        }
                    }
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // 滚动条独立接管右侧窄区域，避免拖动时触发菜单项或整块玻璃形变。
                    if (scrollState.maxValue in 1 until Int.MAX_VALUE &&
                        down.position.x >= size.width - 24.dp.toPx()
                    ) return@awaitEachGesture
                    down.consume()
                    if (!controller.beginPress(
                            morph.card.topLeft + down.position,
                            allowGrowth = scrollState.maxValue <= 0,
                        )) return@awaitEachGesture
                    val pressOrigin = morph.card.topLeft + down.position
                    var previousScroll = scrollState.value
                    var edgePull = 0f
                    var scrollGesture = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) break
                            if (event.changes.any { it.id != down.id && it.pressed }) {
                                event.changes.forEach { it.consume() }
                                controller.cancelPress()
                                continue
                            }
                            val position = morph.card.topLeft + change.position
                            if (!change.pressed) {
                                change.consume()
                                controller.releasePress(position)
                                break
                            }
                            // 无需滚动的短菜单由宿主接管滑选，避免子条目接走移动后取消跨项反馈。
                            if (scrollState.maxValue <= 0) change.consume()
                            // 长菜单仍由列表优先滚动，滚动后本轮松手不提交选项。
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            val finalChange = finalEvent.changes.firstOrNull { it.id == down.id }
                            if (finalChange == null) break
                            val scrolled = finalChange.isConsumed && finalChange.position != finalChange.previousPosition
                            val nextScroll = scrollState.value
                            scrollGesture = scrollGesture || scrolled || nextScroll != previousScroll ||
                                (finalChange.position - down.position).getDistance() > touchSlop
                            val remaining = popupPanelOverscrollDelta(
                                fingerDelta = finalChange.position.y - finalChange.previousPosition.y,
                                scrollBefore = previousScroll,
                                scrollAfter = nextScroll,
                                maxScroll = scrollState.maxValue,
                            )
                            previousScroll = nextScroll
                            if (scrollState.maxValue > 0) {
                                // 列表内位移不累积为拉伸量，否则到边界时形变早已饱和。
                                edgePull = if (remaining == 0f) 0f else edgePull + remaining
                                val beyondEdge = scrollGesture && kotlin.math.abs(edgePull) > touchSlop
                                val pull = if (beyondEdge) edgePull else 0f
                                // 列表滚动不覆盖真实越界坐标：手指拖出面板后持续拉伸，
                                // 即便该帧仍有滚动消费，也不能把形变重新归零。
                                val outside = popupPullBeyondBounds(position, morph.card) != Offset.Zero
                                controller.movePress(
                                    if (outside) position else pressOrigin + Offset(0f, pull),
                                    allowSelection = !scrollGesture,
                                    allowDeformation = outside || beyondEdge,
                                    fromScrollBoundary = !outside,
                                )
                            } else {
                                controller.movePress(position)
                            }
                        }
                    } finally {
                        controller.cancelPress()
                    }
                }
            },
    ) {
        // 光学范围固定覆盖卡片与源控件；只改变轮廓，采样不随卡片缩放。
        Box(Modifier.matchParentSize().popupSurfaceEnvelope(morph, densityScale)
            .graphicsLayer {
                // 最终光学层显式按同帧轮廓裁切，拖动时磨砂、遮色和描边保留同一圆角。
                clip = true
                shape = PopupSurfaceShape(outlineCache.outline(
                    morph.frame(cornerRadiusPx, densityScale),
                    glassPopupSurfaceBounds(morph.card, morph.anchor, densityScale).topLeft,
                    densityScale,
                ))
            }
            .then(panelSurfaceSource?.modifier ?: Modifier)) {
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    val frame = morph.frame(cornerRadiusPx, densityScale)
                    alpha = if (morph.card.isEmpty) 0f else frame.surfaceAlpha
                    shape = PopupSurfaceShape(outlineCache.outline(frame,
                        glassPopupSurfaceBounds(morph.card, morph.anchor, densityScale).topLeft, densityScale))
                    shadowElevation = 12.dp.toPx()
                    ambientShadowColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.14f)
                    spotShadowColor = ambientShadowColor
                },
            )
            Box(
                Modifier.matchParentSize().drawBackdrop(
                    backdrop = backdrop,
                    shape = { PopupSurfaceShape(outlineCache.outline(morph.frame(cornerRadiusPx, densityScale),
                        glassPopupSurfaceBounds(morph.card, morph.anchor, densityScale).topLeft, densityScale)) },
                    effects = effects,
                    highlight = null,
                    onDrawFront = onDrawRim,
                    layerBlock = materialLayer,
                    onDrawSurface = onDrawSurface,
                ),
            )
        }
        val sourceLayer = request.sourceLayer?.takeUnless { it.isReleased }
        val sourceBounds = request.menu?.sourceBounds?.invoke() ?: request.controlBoundsInWindow
        if (sourceLayer != null && sourceBounds != null && !request.centered) {
            Canvas(
                modifier = Modifier.matchParentSize().graphicsLayer {
                    val frame = morph.frame(cornerRadiusPx, densityScale)
                    val target = frame.sourceRect
                    transformOrigin = TransformOrigin(0f, 0f)
                    if (target == null || sourceBounds.isEmpty || morph.card.isEmpty) {
                        alpha = 0f
                    } else {
                        scaleX = target.width / sourceBounds.width
                        scaleY = target.height / sourceBounds.height
                        translationX = target.left - morph.card.left
                        translationY = target.top - morph.card.top
                        alpha = frame.sourceAlpha
                        renderEffect = if (!lowBlurBudget && frame.sourceBlur > 0.1f) sourceBlurCache.resolve(frame.sourceBlur) else null
                    }
                },
            ) { drawLayer(sourceLayer) }
        }
        // 文字始终等比缩放，玻璃高度和圆角由独立轮廓驱动。
        Box(
            modifier = Modifier
                .graphicsLayer { applyPopupContentFrame(morph, cornerRadiusPx, densityScale) }
                .graphicsLayer {
                    clip = true
                    val frame = morph.frame(cornerRadiusPx, densityScale)
                    shape = PopupFrameShape(frame, morph.card, true)
                    renderEffect = if (!lowBlurBudget && frame.contentBlur > 0.1f) {
                        contentBlurCache.resolve(frame.contentBlur)
                    } else null
                },
        ) {
            Box(
                modifier = Modifier
                    .onGloballyPositioned {
                        if (fitWholeItems) wholeItemViewport.updateViewport(it, scrollState.value)
                    }
                    .then(if (interactivePanel) Modifier else Modifier.verticalScroll(scrollState)),
            ) {
                // 次要文字按玻璃底色叠最差背景校正对比，主文字与状态色沿用原调色板。
                com.android.purebilibili.core.ui.ProvideAppGlassPopupContentColors(
                    glassSurfaceColor = surfaceColor,
                ) {
                Column(
                    modifier = Modifier
                        .then(if (request.menu?.interactiveContent == true) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max))
                        .selectableGroup()
                        .padding(PopupItemInset)
                        .then(if (!interactivePanel && request.menu?.showScrollbar == true) Modifier.padding(end = 12.dp) else Modifier),
                ) {
                    request.menu?.let { menu ->
                        Box(menu.modifier) { menu.content(contentMenuScope) }
                    }
                    request.options.forEachIndexed { index, option ->
                        val selected = option.value == request.selectedValue
                        val pressedColor = AppContentColors.primaryText().copy(alpha = 0.12f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned {
                                    controller.registerRow(index, it)
                                    if (selected) selectedItemBounds = Rect(it.positionInParent(),
                                        Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                }
                                .clip(RoundedCornerShape(PopupItemCornerRadius))
                                .drawBehind {
                                    // 按住滑到哪一项就高亮哪一项，勾选与保存仍在松手后改变。
                                    if (controller.pressedIndex == index) drawRect(pressedColor)
                                }
                                .selectable(selected = selected, role = Role.RadioButton,
                                    onClick = { controller.select(option.value) })
                                .padding(
                                    start = OptionStartPadding,
                                    end = OptionEndPadding,
                                    top = OptionVerticalPadding,
                                    bottom = OptionVerticalPadding,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            request.optionLeading?.let { leading ->
                                leading(option.value)
                                Spacer(modifier = Modifier.width(14.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = option.label,
                                    tapToCopyEnabled = false,
                                    // 保持各主题原生菜单的字号和字重，玻璃只改变弹窗材质。
                                    style = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
                                        MaterialTheme.typography.bodyMedium
                                    } else MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
                                        FontWeight.Medium
                                    } else null,
                                    color = AppContentColors.primaryText(),
                                )
                                option.description?.let { description ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AppText(
                                        text = description,
                                        tapToCopyEnabled = false,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText(),
                                    )
                                }
                            }
                            if (selected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AppContentColors.primaryText(),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                }
            }
            if (!interactivePanel && request.menu?.showScrollbar == true) {
                Box(Modifier.matchParentSize()) {
                    com.android.purebilibili.core.ui.components.AppMenuScrollbar(
                        scrollState = scrollState,
                        glass = true,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}
