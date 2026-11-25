package com.android.purebilibili.core.input.gamepad

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max

private const val TAG = "GamepadControlEngine"
private const val CURSOR_AUTO_HIDE_MS = 4_500L
private const val HOVER_DISPATCH_INTERVAL_MS = 32L
private const val LONG_PRESS_FULLSCREEN_MS = 520L
private const val FULLSCREEN_STICK_SEEK_INTERVAL_MS = 320L

data class GamepadCursorState(
    val visible: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    val pressed: Boolean = false,
    val precisionAimHeld: Boolean = false,
)

data class GamepadUiState(
    val pluginEnabled: Boolean = false,
    val connected: Boolean = false,
    val controllerName: String? = null,
    val controllerVendorId: Int = 0,
    val controllerProductId: Int = 0,
    val detectedProfile: GamepadControllerProfile = GamepadControllerProfile.GENERIC,
    val effectiveProfile: GamepadControllerProfile = GamepadControllerProfile.XBOX,
    val mode: GamepadControlMode = GamepadControlMode.CURSOR,
    val scene: GamepadScene = GamepadScene.BROWSING,
    /** 仅在光标显隐离散切换时变化，用于全屏提示栏文案切换；高频坐标更新走 [GamepadControlEngine.cursorState]。 */
    val cursorVisible: Boolean = false,
    val hudVisible: Boolean = false,
    val rtBoostActive: Boolean = false,
    val capturingKeyForSlot: GamepadActionSlot? = null,
    val statusBanner: String? = null,
    val statusBannerId: Long = 0L,
)

data class GamepadLiveTelemetry(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val lastButtonLabel: String = "等待输入",
)

private data class SceneRegistration(
    val token: Any,
    val scene: GamepadScene,
)

/**
 * 多型号手柄全局操控引擎（单例），支持 Xbox、PlayStation (PS4/PS5 DualSense/DualShock)、
 * Nintendo Switch (Pro / Joy-Con) 以及通用蓝牙/USB 手柄。
 *
 * 特性：
 * - 自动识别手柄品牌并适配按键图标、Sony `-1..1` 扳机轴、Switch 数字 ZL/ZR 与 A/B 布局；
 * - 双模式：默认「智能光标模式」+ 一键切换「焦点导航模式」；
 * - 完整自定义键位映射与实体按键录制模式；
 * - 场景感知：视频详情页与全屏页自动解锁播放快捷键与右扳机按住倍速；
 * - 多窗口穿透：自动挂接 Compose Dialog / ModalBottomSheet 窗口并同步光标；
 * - 零空闲开销：仅在摇杆偏离死区或长按连发时运行 Choreographer 帧循环。
 */
object GamepadControlEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var inputManager: InputManager? = null
    private var deviceListenerRegistered = false

    @Volatile
    private var config: GamepadConfig = GamepadConfig()
    private val _configFlow = MutableStateFlow(config)
    val configFlow: StateFlow<GamepadConfig> = _configFlow.asStateFlow()

    private val _uiState = MutableStateFlow(GamepadUiState())
    val uiState: StateFlow<GamepadUiState> = _uiState.asStateFlow()

    private val _cursorState = MutableStateFlow(GamepadCursorState())
    val cursorState: StateFlow<GamepadCursorState> = _cursorState.asStateFlow()

    private val _connectedFlow = MutableStateFlow(false)
    val connectedFlow: StateFlow<Boolean> = _connectedFlow.asStateFlow()

    private val _telemetry = MutableStateFlow(GamepadLiveTelemetry())
    val telemetry: StateFlow<GamepadLiveTelemetry> = _telemetry.asStateFlow()

    private val _navigationActions = MutableSharedFlow<GamepadNavigationAction>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val navigationActions: SharedFlow<GamepadNavigationAction> = _navigationActions.asSharedFlow()

    private val _playerActions = MutableSharedFlow<GamepadPlayerAction>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val playerActions: SharedFlow<GamepadPlayerAction> = _playerActions.asSharedFlow()

    private val sceneStack = CopyOnWriteArrayList<SceneRegistration>()

    private var configPersistCallback: ((GamepadConfig) -> Unit)? = null
    private var keyCaptureCallback: ((Int) -> Unit)? = null

    private val windowBridge = GamepadWindowBridge(
        onKeyEvent = ::handleKeyEvent,
        onGenericMotionEvent = ::handleGenericMotionEvent,
        onRealTouch = ::onRealTouchDetected,
    )

    private var cursorInitialized = false
    private var cursorX = 0f
    private var cursorY = 0f
    private var leftStick = NormalizedStickVector()
    private var rightStick = NormalizedStickVector()
    private var lockedScrollAxis: GamepadScrollAxis? = null
    private var rawHatX = 0f
    private var rawHatY = 0f
    private var analogLeftTriggerValue = 0f
    private var analogRightTriggerValue = 0f
    private var digitalL2Pressed = false
    private var digitalR2Pressed = false
    private var leftTriggerValue = 0f
    private var rightTriggerValue = 0f
    private var ltPressed = false
    private var rtPressed = false
    private var rtSpeedBoostActive = false

    private val hatRepeatTracker = DirectionalRepeatTracker()
    private val leftStickFocusRepeatTracker = DirectionalRepeatTracker(
        initialDelayMs = 280L,
        repeatIntervalMs = 115L,
    )

    private var frameLoopRunning = false
    private var lastFrameNanos = 0L
    private var lastHoverDispatchMs = 0L
    private var lastFullscreenSeekMs = 0L
    private var statusBannerSeq = 0L

    private var fullscreenLongPressFired = false
    private var fullscreenLongPressKeyCode: Int = GamepadKeyCodes.UNBOUND
    private val fullscreenLongPressRunnable = Runnable {
        fullscreenLongPressFired = true
        val state = _uiState.value
        if (state.pluginEnabled && config.playerShortcutsEnabled && isVideoScene(state.scene)) {
            emitPlayerAction(GamepadPlayerAction.TripleAction)
            showBanner("一键三连")
        }
    }

    private val autoHideCursorRunnable = Runnable {
        if (!config.autoHideCursor) return@Runnable
        if (_cursorState.value.pressed) return@Runnable
        hideCursorInternal()
    }

    private val clearBannerRunnable = Runnable {
        _uiState.update { it.copy(statusBanner = null) }
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshConnectedControllers(notifyChange = true)
        override fun onInputDeviceRemoved(deviceId: Int) = refreshConnectedControllers(notifyChange = true)
        override fun onInputDeviceChanged(deviceId: Int) = refreshConnectedControllers(notifyChange = false)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning) return
            val dtSeconds = if (lastFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            }
            lastFrameNanos = frameTimeNanos
            stepFrame(dtSeconds)
            if (shouldKeepFrameLoopRunning()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                stopFrameLoop()
            }
        }
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        inputManager = app.getSystemService(InputManager::class.java)
        if (!deviceListenerRegistered) {
            inputManager?.registerInputDeviceListener(inputDeviceListener, mainHandler)
            deviceListenerRegistered = true
        }
        refreshConnectedControllers(notifyChange = false)
    }

    fun setConfigPersistCallback(callback: ((GamepadConfig) -> Unit)?) {
        configPersistCallback = callback
    }

    fun startKeyCapture(slot: GamepadActionSlot, onCaptured: (Int) -> Unit) {
        keyCaptureCallback = onCaptured
        _uiState.update { it.copy(capturingKeyForSlot = slot) }
    }

    fun stopKeyCapture() {
        keyCaptureCallback = null
        _uiState.update { it.copy(capturingKeyForSlot = null) }
    }

    fun setPluginEnabled(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                pluginEnabled = enabled,
                cursorVisible = if (enabled) current.cursorVisible else false,
                rtBoostActive = false,
            )
        }
        _cursorState.update { current ->
            current.copy(
                visible = if (enabled) current.visible else false,
                pressed = false,
                precisionAimHeld = false,
            )
        }
        runOnMainThread {
            if (!enabled) {
                resetLiveInputs()
                windowBridge.cancelActiveGestures()
                windowBridge.clearDialogCursorOverlay()
            } else {
                refreshConnectedControllers(notifyChange = false)
                windowBridge.syncSubWindowCallbacks()
            }
        }
    }

    fun updateConfig(newConfig: GamepadConfig) {
        val previousMode = config.defaultMode
        config = newConfig
        _configFlow.value = newConfig
        _uiState.update { state ->
            state.copy(
                mode = if (previousMode != newConfig.defaultMode) newConfig.defaultMode else state.mode,
                hudVisible = newConfig.showLegendHud,
                effectiveProfile = resolveEffectiveProfile(
                    override = newConfig.controllerProfileOverride,
                    detected = state.detectedProfile,
                ),
            )
        }
        runOnMainThread {
            if (!newConfig.autoHideCursor) {
                mainHandler.removeCallbacks(autoHideCursorRunnable)
            }
            syncDialogOverlayIfNeeded()
        }
    }

    private inline fun runOnMainThread(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    fun attachActivity(activity: ComponentActivity) {
        initialize(activity)
        windowBridge.attachActivity(activity)
        ensureCursorBoundsInitialized()
        refreshConnectedControllers(notifyChange = false)
    }

    fun detachActivity(activity: ComponentActivity) {
        if (windowBridge.currentActivity() === activity) {
            resetLiveInputs()
        }
        windowBridge.detachActivity(activity)
    }

    fun onActivityWindowFocusChanged(activity: ComponentActivity, hasFocus: Boolean) {
        if (windowBridge.currentActivity() !== activity) return
        // 当 Compose Dialog / ModalBottomSheet 弹出时，Activity 会立即失去窗口焦点，此时挂接弹窗回调。
        if (!hasFocus) {
            mainHandler.post { windowBridge.syncSubWindowCallbacks() }
        } else {
            windowBridge.clearDialogCursorOverlay()
        }
    }

    fun registerScene(token: Any, scene: GamepadScene) {
        sceneStack.removeAll { it.token === token }
        sceneStack.add(SceneRegistration(token, scene))
        publishEffectiveScene()
    }

    fun unregisterScene(token: Any) {
        sceneStack.removeAll { it.token === token }
        publishEffectiveScene()
    }

    /** 避免多个播放器覆层同时存活时重复消费同一条 playerAction（仅栈顶活跃场景响应）。 */
    fun isTopScene(token: Any): Boolean {
        val top = sceneStack.lastOrNull() ?: return true
        return top.token === token
    }

    /** Activity.dispatchTouchEvent 通知真实手指触屏，避免与合成点击混淆。 */
    fun observeActivityTouchEvent(event: MotionEvent) {
        if (windowBridge.isDispatchingSynthetic) return
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        ) {
            onRealTouchDetected()
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (windowBridge.isDispatchingSynthetic) return false
        if (!isGamepadKeyEvent(event)) return false

        val device = event.device
        if (device != null && isHardwareGamepadDevice(device)) {
            syncActiveControllerDevice(device)
        }

        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isUp = event.action == KeyEvent.ACTION_UP
        val isFirstDown = isDown && event.repeatCount == 0

        if (isFirstDown) {
            recordLastButton(keyCode)
        }

        // 自定义键位录制模式：拦截所有可绑定手柄按键并回调给设置弹窗，不触发常规页面操控。
        val captureCallback = keyCaptureCallback
        if (captureCallback != null && keyCode in GamepadKeyCodes.ALL_BINDABLE_KEYS) {
            if (isFirstDown) {
                captureCallback(keyCode)
            }
            return true
        }

        if (!_uiState.value.pluginEnabled) return false

        // 十字键始终走方向导航/播放微调，不参与自定义按键槽位。
        if (keyCode in DPAD_KEYCODES) {
            if (isDown) {
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> GamepadDirection.UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> GamepadDirection.DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> GamepadDirection.LEFT
                    else -> GamepadDirection.RIGHT
                }
                dispatchDirectionAction(direction, fromDpad = true)
            }
            return true
        }

        val state = _uiState.value
        val inVideo = isVideoScene(state.scene) && config.playerShortcutsEnabled
        val inFullscreen = state.scene == GamepadScene.VIDEO_FULLSCREEN && config.playerShortcutsEnabled

        val matchedSlot = resolveActionSlotForKey(
            config = config,
            keyCode = keyCode,
            inVideo = inVideo,
            effectiveProfile = state.effectiveProfile,
        )

        if (matchedSlot != null) {
            return handleActionSlotKey(
                slot = matchedSlot,
                keyCode = keyCode,
                isFirstDown = isFirstDown,
                isUp = isUp,
                state = state,
                inVideo = inVideo,
                inFullscreen = inFullscreen,
            )
        }

        // Switch Pro / 部分第三方手柄的 L2/R2(ZL/ZR) 仅上报数字键而不上报模拟轴，
        // 当用户未将其绑定到其他自定义动作时，作为左/右扳机数字输入无缝接入精准瞄准与倍速。
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            if (isDown || isUp) {
                if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
                    digitalL2Pressed = isDown
                } else {
                    digitalR2Pressed = isDown
                }
                refreshEffectiveTriggerValues()
            }
            return true
        }

        return false
    }

    private fun handleActionSlotKey(
        slot: GamepadActionSlot,
        keyCode: Int,
        isFirstDown: Boolean,
        isUp: Boolean,
        state: GamepadUiState,
        inVideo: Boolean,
        inFullscreen: Boolean,
    ): Boolean {
        val seekMs = config.seekStepSeconds.coerceIn(3, 60) * 1_000L
        return when (slot) {
            GamepadActionSlot.CONFIRM -> {
                if (inFullscreen && !state.cursorVisible && state.mode == GamepadControlMode.CURSOR) {
                    if (isFirstDown) {
                        emitPlayerAction(GamepadPlayerAction.PlayPause)
                    }
                    return true
                }
                when (state.mode) {
                    GamepadControlMode.CURSOR -> {
                        if (isFirstDown) {
                            wakeCursor()
                            _cursorState.update { it.copy(pressed = true) }
                            syncDialogOverlayIfNeeded()
                            windowBridge.injectPointerDown(cursorX, cursorY)
                        } else if (isUp) {
                            _cursorState.update { it.copy(pressed = false) }
                            syncDialogOverlayIfNeeded()
                            windowBridge.injectPointerUp(cursorX, cursorY)
                            mainHandler.postDelayed({ windowBridge.syncSubWindowCallbacks() }, 120L)
                            scheduleCursorAutoHide()
                        }
                        true
                    }
                    GamepadControlMode.FOCUS -> {
                        if (isFirstDown) {
                            windowBridge.dispatchSyntheticKeyPress(KeyEvent.KEYCODE_DPAD_CENTER)
                        }
                        true
                    }
                }
            }

            GamepadActionSlot.BACK -> {
                if (isFirstDown) {
                    releasePointerIfPressed()
                    windowBridge.dispatchBack()
                }
                // 成对消费 DOWN 与 UP，避免 Android 将未消费的 BUTTON_B 再次回退成 KEYCODE_BACK。
                true
            }

            GamepadActionSlot.TOGGLE_MODE -> {
                if (isFirstDown) {
                    toggleControlMode()
                }
                true
            }

            GamepadActionSlot.TOGGLE_CURSOR -> {
                if (isFirstDown) {
                    toggleCursorVisibility()
                }
                true
            }

            GamepadActionSlot.TOGGLE_HUD -> {
                if (isFirstDown) {
                    val lockBound = resolveBoundKeyCode(
                        config = config,
                        slot = GamepadActionSlot.TOGGLE_SCREEN_LOCK,
                        effectiveProfile = state.effectiveProfile,
                    ) != GamepadKeyCodes.UNBOUND
                    if (inFullscreen && ltPressed && !lockBound) {
                        emitPlayerAction(GamepadPlayerAction.ToggleScreenLock)
                    } else {
                        val nextHud = !config.showLegendHud
                        val updatedConfig = config.copy(showLegendHud = nextHud)
                        updateConfig(updatedConfig)
                        configPersistCallback?.invoke(updatedConfig)
                        showBanner(if (nextHud) "已显示底部提示栏" else "已隐藏底部提示栏")
                    }
                }
                true
            }

            GamepadActionSlot.OPEN_SEARCH -> {
                if (isFirstDown) {
                    _navigationActions.tryEmit(GamepadNavigationAction.OpenSearch)
                }
                true
            }

            GamepadActionSlot.REFRESH_OR_TOP -> {
                if (isFirstDown) {
                    _navigationActions.tryEmit(GamepadNavigationAction.ReselectCurrentTab)
                }
                true
            }

            GamepadActionSlot.PREV_TAB -> {
                if (isFirstDown) {
                    _navigationActions.tryEmit(GamepadNavigationAction.SwitchBottomTabDelta(-1))
                }
                true
            }

            GamepadActionSlot.NEXT_TAB -> {
                if (isFirstDown) {
                    _navigationActions.tryEmit(GamepadNavigationAction.SwitchBottomTabDelta(+1))
                }
                true
            }

            GamepadActionSlot.OPEN_SETTINGS -> {
                if (isFirstDown) {
                    _navigationActions.tryEmit(GamepadNavigationAction.OpenSettings)
                }
                true
            }

            GamepadActionSlot.PLAY_PAUSE -> {
                if (isFirstDown) {
                    emitPlayerAction(GamepadPlayerAction.PlayPause)
                }
                true
            }

            GamepadActionSlot.TOGGLE_DANMAKU -> {
                if (isFirstDown) {
                    emitPlayerAction(GamepadPlayerAction.ToggleDanmaku)
                }
                true
            }

            GamepadActionSlot.TOGGLE_FULLSCREEN -> {
                if (isFirstDown) {
                    fullscreenLongPressFired = false
                    fullscreenLongPressKeyCode = keyCode
                    if (inVideo) {
                        mainHandler.removeCallbacks(fullscreenLongPressRunnable)
                        mainHandler.postDelayed(fullscreenLongPressRunnable, LONG_PRESS_FULLSCREEN_MS)
                    }
                } else if (isUp && fullscreenLongPressKeyCode == keyCode) {
                    mainHandler.removeCallbacks(fullscreenLongPressRunnable)
                    if (inVideo && !fullscreenLongPressFired) {
                        emitPlayerAction(GamepadPlayerAction.ToggleFullscreen)
                    }
                    fullscreenLongPressFired = false
                    fullscreenLongPressKeyCode = GamepadKeyCodes.UNBOUND
                }
                true
            }

            GamepadActionSlot.SEEK_BACKWARD -> {
                if (isFirstDown) {
                    if (ltPressed) {
                        emitPlayerAction(GamepadPlayerAction.PreviousPart)
                    } else {
                        emitPlayerAction(GamepadPlayerAction.SeekRelative(-seekMs))
                    }
                }
                true
            }

            GamepadActionSlot.SEEK_FORWARD -> {
                if (isFirstDown) {
                    if (ltPressed) {
                        emitPlayerAction(GamepadPlayerAction.NextPart)
                    } else {
                        emitPlayerAction(GamepadPlayerAction.SeekRelative(seekMs))
                    }
                }
                true
            }

            GamepadActionSlot.TOGGLE_MUTE -> {
                if (isFirstDown) {
                    emitPlayerAction(GamepadPlayerAction.ToggleMute)
                    showBanner("切换静音")
                }
                true
            }

            GamepadActionSlot.TOGGLE_LIKE -> {
                if (isFirstDown) {
                    emitPlayerAction(GamepadPlayerAction.ToggleLike)
                    showBanner("点赞")
                }
                true
            }

            GamepadActionSlot.TOGGLE_SCREEN_LOCK -> {
                if (isFirstDown) {
                    emitPlayerAction(GamepadPlayerAction.ToggleScreenLock)
                }
                true
            }
        }
    }

    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (windowBridge.isDispatchingSynthetic) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false
        val isJoystick = event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!isJoystick) return false

        val device = event.device
        if (device != null && isHardwareGamepadDevice(device)) {
            syncActiveControllerDevice(device)
        }

        // 即使处于按键录制态或插件关闭，也保持遥测更新方便设置页测试仪查看，但仅在插件启用且非录制时执行操控。
        val rawLx = event.getAxisValue(MotionEvent.AXIS_X)
        val rawLy = event.getAxisValue(MotionEvent.AXIS_Y)
        val (rawRx, rawRy, parsedLt, parsedRt) = extractRightStickAndTriggers(event, device)
        rawHatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        rawHatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        analogLeftTriggerValue = parsedLt
        analogRightTriggerValue = parsedRt

        leftStick = normalizeRadialStick(rawLx, rawLy, config.stickDeadzone)
        rightStick = normalizeRadialStick(rawRx, rawRy, config.stickDeadzone)

        if (keyCaptureCallback != null || !_uiState.value.pluginEnabled) {
            leftTriggerValue = resolveMultiControllerTriggerValue(analogLeftTriggerValue, digitalL2Pressed)
            rightTriggerValue = resolveMultiControllerTriggerValue(analogRightTriggerValue, digitalR2Pressed)
            publishTelemetry()
            return keyCaptureCallback != null
        }

        refreshEffectiveTriggerValues()

        val hatDirection = resolveDominantDirection(rawHatX, rawHatY)
        hatRepeatTracker.update(hatDirection, SystemClock.uptimeMillis())?.let { dir ->
            recordLastDirection("十字键", dir)
            dispatchDirectionAction(dir, fromDpad = true)
        }

        if (leftStick.isActive && _uiState.value.mode == GamepadControlMode.CURSOR) {
            wakeCursor()
        }

        if (!rightStick.isActive && !(rtPressed && !isVideoScene(_uiState.value.scene))) {
            lockedScrollAxis = null
            windowBridge.finishScrollDragIfActive()
        }

        if (shouldKeepFrameLoopRunning()) {
            startFrameLoop()
        }
        return true
    }

    private data class RightStickAndTriggers(
        val rightX: Float,
        val rightY: Float,
        val leftTrigger: Float,
        val rightTrigger: Float,
    )

    /**
     * 兼容多品牌手柄的右摇杆与模拟扳机轴提取：
     * - 标准 Xbox / DualSense / 通用：右摇杆使用 `AXIS_Z / AXIS_RZ`，扳机使用 `AXIS_LTRIGGER / AXIS_RTRIGGER` 或 `AXIS_BRAKE / AXIS_GAS`；
     * - 部分 PlayStation DualShock 4 / DualSense 蓝牙驱动：右摇杆用 `Z / RZ`，但 L2 / R2 上报在 `AXIS_RX / AXIS_RY` 且量程为 `[-1, 1]`（松开为 -1）；
     * - 仅具备 `RX / RY` 的老手柄：右摇杆回退到 `AXIS_RX / AXIS_RY`。
     */
    private fun extractRightStickAndTriggers(
        event: MotionEvent,
        device: InputDevice?,
    ): RightStickAndTriggers {
        val zRange = device?.getMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK)
        val rzRange = device?.getMotionRange(MotionEvent.AXIS_RZ, InputDevice.SOURCE_JOYSTICK)
        val rxRange = device?.getMotionRange(MotionEvent.AXIS_RX, InputDevice.SOURCE_JOYSTICK)
        val ryRange = device?.getMotionRange(MotionEvent.AXIS_RY, InputDevice.SOURCE_JOYSTICK)
        val hasZRightStick = zRange != null || rzRange != null || device == null

        val rightX = if (hasZRightStick) {
            event.getAxisValue(MotionEvent.AXIS_Z)
        } else {
            event.getAxisValue(MotionEvent.AXIS_RX)
        }
        val rightY = if (hasZRightStick) {
            event.getAxisValue(MotionEvent.AXIS_RZ)
        } else {
            event.getAxisValue(MotionEvent.AXIS_RY)
        }

        val lTriggerRange = device?.getMotionRange(MotionEvent.AXIS_LTRIGGER, InputDevice.SOURCE_JOYSTICK)
        val brakeRange = device?.getMotionRange(MotionEvent.AXIS_BRAKE, InputDevice.SOURCE_JOYSTICK)
        val rTriggerRange = device?.getMotionRange(MotionEvent.AXIS_RTRIGGER, InputDevice.SOURCE_JOYSTICK)
        val gasRange = device?.getMotionRange(MotionEvent.AXIS_GAS, InputDevice.SOURCE_JOYSTICK)

        var lt = resolveTriggerValue(
            primaryAxis = readNormalizedTriggerAxis(event, MotionEvent.AXIS_LTRIGGER, lTriggerRange),
            fallbackAxis = readNormalizedTriggerAxis(event, MotionEvent.AXIS_BRAKE, brakeRange),
        )
        var rt = resolveTriggerValue(
            primaryAxis = readNormalizedTriggerAxis(event, MotionEvent.AXIS_RTRIGGER, rTriggerRange),
            fallbackAxis = readNormalizedTriggerAxis(event, MotionEvent.AXIS_GAS, gasRange),
        )

        // Sony DualShock 4 / 部分 DualSense 蓝牙映射：无标准 LTRIGGER/BRAKE 轴，改用 RX/RY [-1..1] 表示 L2/R2。
        if (hasZRightStick && lTriggerRange == null && brakeRange == null && rxRange != null) {
            lt = max(lt, normalizeAxisToUnitRange(event.getAxisValue(MotionEvent.AXIS_RX), rxRange.min, rxRange.max))
        }
        if (hasZRightStick && rTriggerRange == null && gasRange == null && ryRange != null) {
            rt = max(rt, normalizeAxisToUnitRange(event.getAxisValue(MotionEvent.AXIS_RY), ryRange.min, ryRange.max))
        }

        return RightStickAndTriggers(
            rightX = rightX,
            rightY = rightY,
            leftTrigger = lt,
            rightTrigger = rt,
        )
    }

    private fun readNormalizedTriggerAxis(
        event: MotionEvent,
        axis: Int,
        range: InputDevice.MotionRange?,
    ): Float {
        if (range == null) return event.getAxisValue(axis).coerceIn(0f, 1f)
        val raw = event.getAxisValue(axis)
        return if (range.min < -0.05f) {
            normalizeAxisToUnitRange(raw, range.min, range.max)
        } else {
            raw.coerceIn(0f, 1f)
        }
    }

    private fun refreshEffectiveTriggerValues() {
        leftTriggerValue = resolveMultiControllerTriggerValue(analogLeftTriggerValue, digitalL2Pressed)
        rightTriggerValue = resolveMultiControllerTriggerValue(analogRightTriggerValue, digitalR2Pressed)
        publishTelemetry()
        updateTriggerStates()
        if (shouldKeepFrameLoopRunning()) {
            startFrameLoop()
        }
    }

    private fun publishTelemetry() {
        // 仅在设置页测试仪活跃订阅时推送遥测，避免高频摇杆采样在正常浏览滑动时产生多余对象分配。
        if (_telemetry.subscriptionCount.value <= 0) return
        _telemetry.update {
            it.copy(
                leftX = leftStick.x,
                leftY = leftStick.y,
                rightX = rightStick.x,
                rightY = rightStick.y,
                leftTrigger = leftTriggerValue,
                rightTrigger = rightTriggerValue,
            )
        }
    }

    private fun updateTriggerStates() {
        val nextLt = resolveTriggerPressed(leftTriggerValue, ltPressed)
        if (nextLt != ltPressed) {
            ltPressed = nextLt
            _cursorState.update { it.copy(precisionAimHeld = nextLt) }
            syncDialogOverlayIfNeeded()
        }

        val nextRt = resolveTriggerPressed(rightTriggerValue, rtPressed)
        if (nextRt != rtPressed) {
            rtPressed = nextRt
            val state = _uiState.value
            val canBoost = config.playerShortcutsEnabled && isVideoScene(state.scene)
            if (nextRt && canBoost) {
                rtSpeedBoostActive = true
                _uiState.update { it.copy(rtBoostActive = true) }
                emitPlayerAction(GamepadPlayerAction.SpeedBoostChanged(active = true, speed = config.rtBoostSpeed))
                val (_, rtLabel) = formatTriggerPairLabels(state.effectiveProfile)
                showBanner("${config.rtBoostSpeed}x 倍速中（松开 $rtLabel 恢复）")
            } else if (!nextRt && rtSpeedBoostActive) {
                rtSpeedBoostActive = false
                _uiState.update { it.copy(rtBoostActive = false) }
                emitPlayerAction(GamepadPlayerAction.SpeedBoostChanged(active = false, speed = 1.0f))
            }
        }
    }

    private fun stepFrame(dtSeconds: Float) {
        val state = _uiState.value
        if (!state.pluginEnabled) return
        val nowMs = SystemClock.uptimeMillis()
        val density = windowBridge.currentActivity()?.resources?.displayMetrics?.density ?: 2f

        // 1. 蓝牙手柄十字键（HAT 轴）长按连发
        val hatDirection = resolveDominantDirection(rawHatX, rawHatY)
        hatRepeatTracker.update(hatDirection, nowMs)?.let { dir ->
            dispatchDirectionAction(dir, fromDpad = true)
        }

        // 2. 左摇杆：光标模式移动光标，焦点模式驱动方向键步进
        if (leftStick.isActive) {
            when (state.mode) {
                GamepadControlMode.CURSOR -> {
                    ensureCursorBoundsInitialized()
                    val (viewportW, viewportH) = windowBridge.currentViewportSize()
                    val (dx, dy) = computeCursorStepPx(
                        vector = leftStick,
                        deltaSeconds = dtSeconds,
                        density = density,
                        sensitivity = config.cursorSensitivity,
                        precisionAimHeld = ltPressed,
                    )
                    val nextX = (cursorX + dx).coerceIn(0f, (viewportW - 1).coerceAtLeast(1).toFloat())
                    val nextY = (cursorY + dy).coerceIn(0f, (viewportH - 1).coerceAtLeast(1).toFloat())
                    if (nextX != cursorX || nextY != cursorY) {
                        cursorX = nextX
                        cursorY = nextY
                        if (!_uiState.value.cursorVisible) {
                            _uiState.update { it.copy(cursorVisible = true) }
                        }
                        _cursorState.update {
                            it.copy(
                                visible = true,
                                x = cursorX,
                                y = cursorY,
                            )
                        }
                        syncDialogOverlayIfNeeded()
                        if (_cursorState.value.pressed) {
                            windowBridge.injectPointerMove(cursorX, cursorY)
                        } else if (config.hoverHighlightEnabled && nowMs - lastHoverDispatchMs >= HOVER_DISPATCH_INTERVAL_MS) {
                            lastHoverDispatchMs = nowMs
                            windowBridge.injectHover(cursorX, cursorY)
                        }
                    }
                    scheduleCursorAutoHide()
                }
                GamepadControlMode.FOCUS -> {
                    val stickDir = resolveDominantDirection(leftStick.x, leftStick.y, threshold = 0.25f)
                    leftStickFocusRepeatTracker.update(stickDir, nowMs)?.let { dir ->
                        dispatchDirectionAction(dir, fromDpad = false)
                    }
                }
            }
        } else {
            leftStickFocusRepeatTracker.reset()
        }

        // 3. 右摇杆（及浏览模式下右扳机加速向下滚动）
        val inFullscreenNoCursor = state.scene == GamepadScene.VIDEO_FULLSCREEN &&
            config.playerShortcutsEnabled &&
            !state.cursorVisible
        if (inFullscreenNoCursor && rightStick.isActive) {
            lockedScrollAxis = null
            windowBridge.finishScrollDragIfActive(cancel = true)
            if (abs(rightStick.y) >= abs(rightStick.x)) {
                val deltaBrightness = -rightStick.y * dtSeconds * 0.75f
                if (abs(deltaBrightness) > 0.004f) {
                    emitPlayerAction(GamepadPlayerAction.AdjustBrightness(deltaBrightness))
                }
            } else if (nowMs - lastFullscreenSeekMs >= FULLSCREEN_STICK_SEEK_INTERVAL_MS) {
                lastFullscreenSeekMs = nowMs
                val deltaMs = if (rightStick.x > 0f) 5_000L else -5_000L
                emitPlayerAction(GamepadPlayerAction.SeekRelative(deltaMs))
            }
        } else {
            val rawScrollY = when {
                rightStick.isActive -> rightStick.y
                rtPressed && !isVideoScene(state.scene) -> rightTriggerValue.coerceAtLeast(0.65f)
                else -> 0f
            }
            val rawScrollX = if (rightStick.isActive) rightStick.x else 0f
            val (nextAxis, lockedVector) = resolveLockedScrollVector(
                rawX = rawScrollX,
                rawY = rawScrollY,
                currentAxis = lockedScrollAxis,
            )
            lockedScrollAxis = nextAxis
            if (nextAxis != null && lockedVector.isActive) {
                ensureCursorBoundsInitialized()
                val (dxPx, dyPx) = computeScrollDragStepPx(
                    lockedVector = lockedVector,
                    deltaSeconds = dtSeconds,
                    density = density,
                    scrollSensitivity = config.scrollSensitivity,
                    invertVertical = config.invertRightStickScroll,
                )
                // 始终锚定在当前光标坐标（cursorX, cursorY），光标在哪个模块，右摇杆就在该模块内滚动，
                // 即使光标因静止自动淡出也不再跳回屏幕中央。
                if (state.cursorVisible) {
                    scheduleCursorAutoHide()
                }
                windowBridge.injectContinuousScroll(
                    screenX = cursorX,
                    screenY = cursorY,
                    dxPx = dxPx,
                    dyPx = dyPx,
                    axis = nextAxis,
                    density = density,
                )
            } else {
                windowBridge.finishScrollDragIfActive()
            }
        }
    }

    private fun dispatchDirectionAction(direction: GamepadDirection, fromDpad: Boolean) {
        val state = _uiState.value
        val inVideo = isVideoScene(state.scene) && config.playerShortcutsEnabled
        val useVideoShortcut = inVideo && fromDpad &&
            (state.scene == GamepadScene.VIDEO_FULLSCREEN || state.mode == GamepadControlMode.CURSOR) &&
            !windowBridge.isTextInputFocused()

        if (useVideoShortcut) {
            when (direction) {
                GamepadDirection.LEFT -> emitPlayerAction(GamepadPlayerAction.SeekRelative(-5_000L))
                GamepadDirection.RIGHT -> emitPlayerAction(GamepadPlayerAction.SeekRelative(5_000L))
                GamepadDirection.UP -> emitPlayerAction(GamepadPlayerAction.AdjustVolume(+1))
                GamepadDirection.DOWN -> emitPlayerAction(GamepadPlayerAction.AdjustVolume(-1))
            }
            return
        }

        val keyCode = when (direction) {
            GamepadDirection.UP -> KeyEvent.KEYCODE_DPAD_UP
            GamepadDirection.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            GamepadDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            GamepadDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        }
        windowBridge.dispatchSyntheticKeyPress(keyCode)
    }

    private fun toggleControlMode() {
        val current = _uiState.value.mode
        val next = if (current == GamepadControlMode.CURSOR) {
            GamepadControlMode.FOCUS
        } else {
            GamepadControlMode.CURSOR
        }
        releasePointerIfPressed()
        if (next == GamepadControlMode.FOCUS) {
            hideCursorInternal()
        } else {
            wakeCursor()
        }
        _uiState.update { it.copy(mode = next) }
        showBanner(if (next == GamepadControlMode.CURSOR) "智能光标模式" else "焦点导航模式")
    }

    private fun toggleCursorVisibility() {
        val state = _uiState.value
        if (state.mode == GamepadControlMode.FOCUS) {
            _uiState.update { it.copy(mode = GamepadControlMode.CURSOR) }
            wakeCursor()
            showBanner("已唤出虚拟光标")
            return
        }
        if (state.cursorVisible) {
            hideCursorInternal()
            showBanner("已隐藏虚拟光标")
        } else {
            wakeCursor()
            showBanner("已唤出虚拟光标")
        }
    }

    private fun wakeCursor() {
        ensureCursorBoundsInitialized()
        if (!_uiState.value.cursorVisible) {
            _uiState.update { it.copy(cursorVisible = true) }
        }
        _cursorState.update {
            it.copy(
                visible = true,
                x = cursorX,
                y = cursorY,
            )
        }
        syncDialogOverlayIfNeeded()
        scheduleCursorAutoHide()
    }

    private fun hideCursorInternal() {
        mainHandler.removeCallbacks(autoHideCursorRunnable)
        releasePointerIfPressed()
        if (_uiState.value.cursorVisible) {
            _uiState.update { it.copy(cursorVisible = false) }
        }
        _cursorState.update { it.copy(visible = false, pressed = false) }
        windowBridge.clearDialogCursorOverlay()
    }

    private fun scheduleCursorAutoHide() {
        mainHandler.removeCallbacks(autoHideCursorRunnable)
        if (config.autoHideCursor) {
            mainHandler.postDelayed(autoHideCursorRunnable, CURSOR_AUTO_HIDE_MS)
        }
    }

    private fun onRealTouchDetected() {
        if (!config.hideCursorOnTouch) return
        if (_cursorState.value.visible) {
            hideCursorInternal()
        }
    }

    private fun releasePointerIfPressed() {
        if (_cursorState.value.pressed) {
            _cursorState.update { it.copy(pressed = false) }
            windowBridge.injectPointerUp(cursorX, cursorY)
        }
    }

    private fun ensureCursorBoundsInitialized() {
        val (w, h) = windowBridge.currentViewportSize()
        if (!cursorInitialized) {
            cursorX = w * 0.5f
            cursorY = h * 0.5f
            cursorInitialized = true
            _cursorState.update { it.copy(x = cursorX, y = cursorY) }
        } else {
            cursorX = cursorX.coerceIn(0f, (w - 1).coerceAtLeast(1).toFloat())
            cursorY = cursorY.coerceIn(0f, (h - 1).coerceAtLeast(1).toFloat())
        }
    }

    private fun syncDialogOverlayIfNeeded() {
        val state = _uiState.value
        val cursor = _cursorState.value
        val density = windowBridge.currentActivity()?.resources?.displayMetrics?.density ?: 2f
        windowBridge.syncDialogCursorOverlay(
            visible = state.pluginEnabled && cursor.visible,
            screenX = cursorX,
            screenY = cursorY,
            pressed = cursor.pressed,
            precisionAim = cursor.precisionAimHeld,
            density = density,
            cursorSize = config.cursorSize,
            cursorColorStyle = config.cursorColorStyle,
        )
    }

    private fun publishEffectiveScene() {
        val effective = when {
            sceneStack.any { it.scene == GamepadScene.VIDEO_FULLSCREEN } -> GamepadScene.VIDEO_FULLSCREEN
            sceneStack.any { it.scene == GamepadScene.VIDEO_DETAIL } -> GamepadScene.VIDEO_DETAIL
            else -> GamepadScene.BROWSING
        }
        val previous = _uiState.value.scene
        if (effective != previous) {
            if (!isVideoScene(effective) && rtSpeedBoostActive) {
                rtSpeedBoostActive = false
                _uiState.update { it.copy(rtBoostActive = false) }
                emitPlayerAction(GamepadPlayerAction.SpeedBoostChanged(active = false, speed = 1.0f))
            }
            _uiState.update { it.copy(scene = effective) }
        }
    }

    private fun syncActiveControllerDevice(device: InputDevice) {
        val current = _uiState.value
        val name = device.name?.takeIf { it.isNotBlank() }
        val detected = detectControllerProfile(
            vendorId = device.vendorId,
            productId = device.productId,
            deviceName = name.orEmpty(),
        )
        if (!current.connected ||
            current.controllerName != name ||
            current.controllerVendorId != device.vendorId ||
            current.controllerProductId != device.productId ||
            current.detectedProfile != detected
        ) {
            _connectedFlow.value = true
            _uiState.update {
                it.copy(
                    connected = true,
                    controllerName = name,
                    controllerVendorId = device.vendorId,
                    controllerProductId = device.productId,
                    detectedProfile = detected,
                    effectiveProfile = resolveEffectiveProfile(config.controllerProfileOverride, detected),
                )
            }
        }
    }

    private fun refreshConnectedControllers(notifyChange: Boolean) {
        val manager = inputManager ?: return
        val gamepad = manager.inputDeviceIds
            .asSequence()
            .mapNotNull { manager.getInputDevice(it) }
            .filter { isHardwareGamepadDevice(it) }
            .sortedByDescending { scoreControllerDevice(it) }
            .firstOrNull()
        val wasConnected = _uiState.value.connected
        val isNowConnected = gamepad != null
        val name = gamepad?.name?.takeIf { it.isNotBlank() }
        val vendorId = gamepad?.vendorId ?: 0
        val productId = gamepad?.productId ?: 0
        val detected = if (gamepad != null) {
            detectControllerProfile(vendorId = vendorId, productId = productId, deviceName = name.orEmpty())
        } else {
            GamepadControllerProfile.GENERIC
        }
        val effective = resolveEffectiveProfile(config.controllerProfileOverride, detected)
        _connectedFlow.value = isNowConnected
        _uiState.update {
            it.copy(
                connected = isNowConnected,
                controllerName = name,
                controllerVendorId = vendorId,
                controllerProductId = productId,
                detectedProfile = detected,
                effectiveProfile = effective,
            )
        }
        if (notifyChange && _uiState.value.pluginEnabled && wasConnected != isNowConnected) {
            if (isNowConnected) {
                val brand = controllerProfileBadgeLabel(effective)
                val label = name ?: "$brand 手柄"
                showBanner("已连接 $brand · $label")
                Logger.d(TAG, "Gamepad connected: $label (vid=$vendorId pid=$productId profile=$detected)")
            } else {
                resetLiveInputs()
                hideCursorInternal()
                showBanner("手柄已断开")
                Logger.d(TAG, "Gamepad disconnected")
            }
        }
    }

    /** 优先选择同时具备 SOURCE_GAMEPAD 与 SOURCE_JOYSTICK 的主手柄设备（避开 PS 手柄附带的触控板子设备）。 */
    private fun scoreControllerDevice(device: InputDevice): Int {
        var score = 0
        val sources = device.sources
        if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) score += 2
        if ((sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) score += 2
        if (device.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_JOYSTICK) != null) score += 1
        if (device.name?.contains("touchpad", ignoreCase = true) == true) score -= 4
        return score
    }

    private fun emitPlayerAction(action: GamepadPlayerAction) {
        _playerActions.tryEmit(action)
    }

    private fun showBanner(message: String) {
        statusBannerSeq += 1L
        _uiState.update {
            it.copy(
                statusBanner = message,
                statusBannerId = statusBannerSeq,
            )
        }
        mainHandler.removeCallbacks(clearBannerRunnable)
        mainHandler.postDelayed(clearBannerRunnable, 2_000L)
    }

    private fun shouldKeepFrameLoopRunning(): Boolean {
        if (!_uiState.value.pluginEnabled) return false
        return leftStick.isActive ||
            rightStick.isActive ||
            resolveDominantDirection(rawHatX, rawHatY) != null ||
            (rtPressed && !isVideoScene(_uiState.value.scene))
    }

    private fun startFrameLoop() {
        if (frameLoopRunning) return
        frameLoopRunning = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        if (!frameLoopRunning) return
        frameLoopRunning = false
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lockedScrollAxis = null
        windowBridge.finishScrollDragIfActive()
    }

    private fun resetLiveInputs() {
        stopFrameLoop()
        mainHandler.removeCallbacks(fullscreenLongPressRunnable)
        mainHandler.removeCallbacks(autoHideCursorRunnable)
        fullscreenLongPressFired = false
        fullscreenLongPressKeyCode = GamepadKeyCodes.UNBOUND
        leftStick = NormalizedStickVector()
        rightStick = NormalizedStickVector()
        lockedScrollAxis = null
        rawHatX = 0f
        rawHatY = 0f
        analogLeftTriggerValue = 0f
        analogRightTriggerValue = 0f
        digitalL2Pressed = false
        digitalR2Pressed = false
        leftTriggerValue = 0f
        rightTriggerValue = 0f
        ltPressed = false
        rtPressed = false
        if (rtSpeedBoostActive) {
            rtSpeedBoostActive = false
            emitPlayerAction(GamepadPlayerAction.SpeedBoostChanged(active = false, speed = 1.0f))
        }
        hatRepeatTracker.reset()
        leftStickFocusRepeatTracker.reset()
        windowBridge.finishScrollDragIfActive()
        _telemetry.value = GamepadLiveTelemetry()
        _cursorState.update { it.copy(precisionAimHeld = false) }
        _uiState.update { it.copy(rtBoostActive = false) }
    }

    private fun isVideoScene(scene: GamepadScene): Boolean {
        return scene == GamepadScene.VIDEO_DETAIL || scene == GamepadScene.VIDEO_FULLSCREEN
    }

    private fun isGamepadKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code in GAMEPAD_ONLY_KEYCODES) return true
        val fromGamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        if (code in DPAD_KEYCODES) return fromGamepad
        if (code in GamepadKeyCodes.ALL_BINDABLE_KEYS) return fromGamepad
        return false
    }

    private fun recordLastButton(keyCode: Int) {
        val profile = _uiState.value.effectiveProfile
        val label = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "十字键 上"
            KeyEvent.KEYCODE_DPAD_DOWN -> "十字键 下"
            KeyEvent.KEYCODE_DPAD_LEFT -> "十字键 左"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "十字键 右"
            else -> formatGamepadKeyDisplayName(keyCode, profile)
        }
        _telemetry.update { it.copy(lastButtonLabel = label) }
    }

    private fun recordLastDirection(prefix: String, direction: GamepadDirection) {
        val suffix = when (direction) {
            GamepadDirection.UP -> "上"
            GamepadDirection.DOWN -> "下"
            GamepadDirection.LEFT -> "左"
            GamepadDirection.RIGHT -> "右"
        }
        _telemetry.update { it.copy(lastButtonLabel = "$prefix $suffix") }
    }

    fun hasConnectedGamepad(context: Context): Boolean {
        val manager = context.getSystemService(InputManager::class.java) ?: return false
        return manager.inputDeviceIds.any { id ->
            isHardwareGamepadDevice(manager.getInputDevice(id))
        }
    }

    fun isHardwareGamepadDevice(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !device.isEnabled) return false
        val sources = device.sources
        if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            return true
        }
        if ((sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            val hasStickAxis = device.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_JOYSTICK) != null &&
                device.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_JOYSTICK) != null
            val hasFaceButtons = device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B).any { it }
            return hasStickAxis && hasFaceButtons
        }
        return false
    }

    private val GAMEPAD_ONLY_KEYCODES = setOf(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_C,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_Z,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
    )

    private val DPAD_KEYCODES = setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
    )
}
