package com.android.purebilibili.core.input.gamepad

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

@Serializable
enum class GamepadControlMode {
    CURSOR,
    FOCUS,
}

enum class GamepadScene {
    BROWSING,
    VIDEO_DETAIL,
    VIDEO_FULLSCREEN,
}

enum class GamepadDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

enum class GamepadScrollAxis {
    VERTICAL,
    HORIZONTAL,
}

@Serializable
enum class GamepadControllerProfile {
    AUTO,
    XBOX,
    PLAYSTATION,
    SWITCH,
    GENERIC,
}

@Serializable
enum class GamepadCursorSize(val radiusScale: Float) {
    SMALL(0.8f),
    MEDIUM(1.0f),
    LARGE(1.3f),
}

@Serializable
enum class GamepadCursorColorStyle {
    CLASSIC_WHITE,
    BILI_PINK,
    CYAN,
    GOLD,
}

enum class GamepadActionScope {
    GLOBAL,
    BROWSING,
    PLAYER,
}

/**
 * 与 Android [android.view.KeyEvent] 数值严格一致的手柄按键常量，
 * 避免纯 JVM 单元测试依赖 Android 静态桩返回值。
 */
object GamepadKeyCodes {
    const val UNBOUND = 0
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_C = 98
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_Z = 101
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
    const val BUTTON_MODE = 110
    const val BUTTON_1 = 188
    const val BUTTON_2 = 189
    const val BUTTON_3 = 190
    const val BUTTON_4 = 191
    const val BUTTON_5 = 192
    const val BUTTON_6 = 193
    const val BUTTON_7 = 194
    const val BUTTON_8 = 195
    const val BUTTON_9 = 196
    const val BUTTON_10 = 197
    const val BUTTON_11 = 198
    const val BUTTON_12 = 199
    const val BUTTON_13 = 200
    const val BUTTON_14 = 201
    const val BUTTON_15 = 202
    const val BUTTON_16 = 203

    /** 设置页快速手选与按键录制支持的常用手柄键位列表。 */
    val COMMON_BINDABLE_KEYS: List<Int> = listOf(
        BUTTON_A,
        BUTTON_B,
        BUTTON_X,
        BUTTON_Y,
        BUTTON_L1,
        BUTTON_R1,
        BUTTON_L2,
        BUTTON_R2,
        BUTTON_SELECT,
        BUTTON_START,
        BUTTON_THUMBL,
        BUTTON_THUMBR,
    )

    val ALL_BINDABLE_KEYS: Set<Int> = buildSet {
        addAll(COMMON_BINDABLE_KEYS)
        add(BUTTON_C)
        add(BUTTON_Z)
        for (code in BUTTON_1..BUTTON_16) {
            add(code)
        }
    }
}

enum class GamepadActionSlot(
    val id: String,
    val scope: GamepadActionScope,
    val defaultKeyCode: Int,
    val title: String,
    val description: String,
    val allowUnbind: Boolean = true,
) {
    CONFIRM(
        id = "confirm",
        scope = GamepadActionScope.GLOBAL,
        defaultKeyCode = GamepadKeyCodes.BUTTON_A,
        title = "确认 / 点击 / 拖拽",
        description = "光标模式点击或按住拖拽，焦点模式确认",
        allowUnbind = false,
    ),
    BACK(
        id = "back",
        scope = GamepadActionScope.GLOBAL,
        defaultKeyCode = GamepadKeyCodes.BUTTON_B,
        title = "返回 / 退出",
        description = "优先关闭弹窗或退出全屏，再返回上一页",
        allowUnbind = false,
    ),
    TOGGLE_MODE(
        id = "toggle_mode",
        scope = GamepadActionScope.GLOBAL,
        defaultKeyCode = GamepadKeyCodes.BUTTON_SELECT,
        title = "切换光标 / 焦点模式",
        description = "在智能光标模式与焦点导航模式间一键切换",
    ),
    TOGGLE_CURSOR(
        id = "toggle_cursor",
        scope = GamepadActionScope.GLOBAL,
        defaultKeyCode = GamepadKeyCodes.BUTTON_THUMBL,
        title = "显示 / 隐藏虚拟光标",
        description = "快速唤出或隐藏屏幕上的虚拟光标",
    ),
    TOGGLE_HUD(
        id = "toggle_hud",
        scope = GamepadActionScope.GLOBAL,
        defaultKeyCode = GamepadKeyCodes.BUTTON_THUMBR,
        title = "显示 / 隐藏底部提示栏",
        description = "随时开关屏幕底部的手柄键位提示条",
    ),
    OPEN_SEARCH(
        id = "open_search",
        scope = GamepadActionScope.BROWSING,
        defaultKeyCode = GamepadKeyCodes.BUTTON_X,
        title = "打开搜索",
        description = "在浏览页面快速呼出搜索页",
    ),
    REFRESH_OR_TOP(
        id = "refresh_or_top",
        scope = GamepadActionScope.BROWSING,
        defaultKeyCode = GamepadKeyCodes.BUTTON_Y,
        title = "回顶 / 刷新当前页",
        description = "再次触发当前底栏标签以回到顶部或刷新",
    ),
    PREV_TAB(
        id = "prev_tab",
        scope = GamepadActionScope.BROWSING,
        defaultKeyCode = GamepadKeyCodes.BUTTON_L1,
        title = "上一个底栏标签",
        description = "向左切换首页、动态、我的等主标签页",
    ),
    NEXT_TAB(
        id = "next_tab",
        scope = GamepadActionScope.BROWSING,
        defaultKeyCode = GamepadKeyCodes.BUTTON_R1,
        title = "下一个底栏标签",
        description = "向右切换首页、动态、我的等主标签页",
    ),
    OPEN_SETTINGS(
        id = "open_settings",
        scope = GamepadActionScope.BROWSING,
        defaultKeyCode = GamepadKeyCodes.BUTTON_START,
        title = "打开设置",
        description = "在非播放页面快捷进入软件设置",
    ),
    PLAY_PAUSE(
        id = "play_pause",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.BUTTON_START,
        title = "播放 / 暂停",
        description = "视频详情页与全屏播放页快捷播停",
    ),
    TOGGLE_DANMAKU(
        id = "toggle_danmaku",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.BUTTON_X,
        title = "开关弹幕",
        description = "播放视频时一键开启或关闭弹幕",
    ),
    TOGGLE_FULLSCREEN(
        id = "toggle_fullscreen",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.BUTTON_Y,
        title = "全屏切换（长按三连）",
        description = "短按进出全屏，长按触发一键三连",
    ),
    SEEK_BACKWARD(
        id = "seek_backward",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.BUTTON_L1,
        title = "快退（按住左扳机上一 P）",
        description = "按设定步长快退；按住左扳机时切至上一分 P",
    ),
    SEEK_FORWARD(
        id = "seek_forward",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.BUTTON_R1,
        title = "快进（按住左扳机下一 P）",
        description = "按设定步长快进；按住左扳机时切至下一分 P",
    ),
    TOGGLE_MUTE(
        id = "toggle_mute",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.UNBOUND,
        title = "静音开关（可选）",
        description = "默认未绑定，可绑定到空闲按键快速静音",
    ),
    TOGGLE_LIKE(
        id = "toggle_like",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.UNBOUND,
        title = "点赞视频（可选）",
        description = "默认未绑定，可绑定到空闲按键快速点赞",
    ),
    TOGGLE_SCREEN_LOCK(
        id = "toggle_screen_lock",
        scope = GamepadActionScope.PLAYER,
        defaultKeyCode = GamepadKeyCodes.UNBOUND,
        title = "全屏锁屏（可选）",
        description = "默认未绑定（全屏按住左扳机+R3亦可触发）",
    ),
}

@Serializable
data class GamepadConfig(
    val defaultMode: GamepadControlMode = GamepadControlMode.CURSOR,
    /** 光标基准移速倍率（0.5f..2.5f） */
    val cursorSensitivity: Float = 1.0f,
    /** 右摇杆滚动速度倍率（0.5f..2.5f） */
    val scrollSensitivity: Float = 1.0f,
    /** 摇杆径向死区（0.08f..0.35f） */
    val stickDeadzone: Float = 0.15f,
    /** 播放页按住右扳机时的倍速倍率 */
    val rtBoostSpeed: Float = 2.0f,
    /** 是否显示底部手柄键位提示条（默认关闭，避免干扰底部导航栏） */
    val showLegendHud: Boolean = false,
    /** 光标移动时是否向底层发送 Hover 事件以触发卡片悬浮反馈 */
    val hoverHighlightEnabled: Boolean = true,
    /** 静止一段时间后自动淡出光标 */
    val autoHideCursor: Boolean = true,
    /** 检测到真实触屏输入时自动隐藏光标 */
    val hideCursorOnTouch: Boolean = true,
    /** 视频页与全屏页启用手柄专属快捷操控 */
    val playerShortcutsEnabled: Boolean = true,
    /** 反转右摇杆垂直滚动方向 */
    val invertRightStickScroll: Boolean = false,
    /** 手柄型号与按键图标方案（AUTO 表示根据连接手柄自动识别） */
    val controllerProfileOverride: GamepadControllerProfile = GamepadControllerProfile.AUTO,
    /** 手动互换确认键与返回键（适配日式 PS ○确认 / ✕返回 或 Switch 习惯） */
    val swapConfirmBack: Boolean = false,
    /** 检测到 Nintendo Switch 手柄时自动适配右侧 A 确认、下方 B 返回 */
    val autoSwapSwitchConfirmBack: Boolean = true,
    /** 播放页肩键快进/快退步长（秒） */
    val seekStepSeconds: Int = 10,
    /** 虚拟光标尺寸 */
    val cursorSize: GamepadCursorSize = GamepadCursorSize.MEDIUM,
    /** 虚拟光标颜色主题 */
    val cursorColorStyle: GamepadCursorColorStyle = GamepadCursorColorStyle.CLASSIC_WHITE,
    /** 自定义键位覆盖：slotId -> Android KeyEvent.KEYCODE_*（0 表示解除绑定） */
    val customBindings: Map<String, Int> = emptyMap(),
)

data class NormalizedStickVector(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    val isActive: Boolean get() = x != 0f || y != 0f
}

private const val VENDOR_MICROSOFT = 0x045E
private const val VENDOR_SONY = 0x054C
private const val VENDOR_NINTENDO = 0x057E
private const val VENDOR_8BITDO = 0x2DC8

/** 根据 USB/蓝牙 Vendor ID 与设备名称自动识别手柄型号。 */
fun detectControllerProfile(
    vendorId: Int,
    productId: Int = 0,
    deviceName: String = "",
): GamepadControllerProfile {
    val lower = deviceName.lowercase()
    return when {
        vendorId == VENDOR_SONY -> GamepadControllerProfile.PLAYSTATION
        vendorId == VENDOR_MICROSOFT -> GamepadControllerProfile.XBOX
        vendorId == VENDOR_NINTENDO -> GamepadControllerProfile.SWITCH
        lower.contains("dualsense") ||
            lower.contains("dualshock") ||
            lower.contains("playstation") ||
            lower.contains("ps5") ||
            lower.contains("ps4") ||
            lower.contains("ps3") ||
            lower.contains("sony") -> GamepadControllerProfile.PLAYSTATION
        lower.contains("nintendo") ||
            lower.contains("joy-con") ||
            lower.contains("joycon") ||
            lower.contains("switch") ||
            lower.contains("pro controller") -> GamepadControllerProfile.SWITCH
        lower.contains("xbox") ||
            lower.contains("x-box") ||
            lower.contains("xinput") ||
            lower.contains("microsoft") ||
            lower.contains("elite wireless") -> GamepadControllerProfile.XBOX
        vendorId == VENDOR_8BITDO -> {
            if (lower.contains("switch")) GamepadControllerProfile.SWITCH
            else if (lower.contains("ps")) GamepadControllerProfile.PLAYSTATION
            else GamepadControllerProfile.XBOX
        }
        lower.contains("wireless controller") && productId in setOf(0x05C4, 0x09CC, 0x0CE6, 0x0DF2) ->
            GamepadControllerProfile.PLAYSTATION
        else -> GamepadControllerProfile.GENERIC
    }
}

fun resolveEffectiveProfile(
    override: GamepadControllerProfile,
    detected: GamepadControllerProfile,
): GamepadControllerProfile {
    if (override != GamepadControllerProfile.AUTO) return override
    return if (detected == GamepadControllerProfile.AUTO) {
        GamepadControllerProfile.XBOX
    } else {
        detected
    }
}

fun controllerProfileDisplayName(profile: GamepadControllerProfile): String = when (profile) {
    GamepadControllerProfile.AUTO -> "自动识别"
    GamepadControllerProfile.XBOX -> "Xbox"
    GamepadControllerProfile.PLAYSTATION -> "PlayStation (PS4/PS5)"
    GamepadControllerProfile.SWITCH -> "Nintendo Switch"
    GamepadControllerProfile.GENERIC -> "通用手柄"
}

fun controllerProfileBadgeLabel(profile: GamepadControllerProfile): String = when (profile) {
    GamepadControllerProfile.XBOX -> "Xbox"
    GamepadControllerProfile.PLAYSTATION -> "PlayStation"
    GamepadControllerProfile.SWITCH -> "Switch"
    GamepadControllerProfile.GENERIC,
    GamepadControllerProfile.AUTO -> "通用手柄"
}

fun cursorSizeDisplayName(size: GamepadCursorSize): String = when (size) {
    GamepadCursorSize.SMALL -> "小"
    GamepadCursorSize.MEDIUM -> "中（默认）"
    GamepadCursorSize.LARGE -> "大"
}

fun cursorColorDisplayName(style: GamepadCursorColorStyle): String = when (style) {
    GamepadCursorColorStyle.CLASSIC_WHITE -> "经典白"
    GamepadCursorColorStyle.BILI_PINK -> "哔哩粉"
    GamepadCursorColorStyle.CYAN -> "冰晶青"
    GamepadCursorColorStyle.GOLD -> "琥珀金"
}

fun resolveEffectiveSwapConfirmBack(
    config: GamepadConfig,
    effectiveProfile: GamepadControllerProfile,
): Boolean {
    if (config.swapConfirmBack) return true
    return effectiveProfile == GamepadControllerProfile.SWITCH && config.autoSwapSwitchConfirmBack
}

fun defaultKeyCodeForSlot(
    slot: GamepadActionSlot,
    swapConfirmBack: Boolean,
): Int {
    if (!swapConfirmBack) return slot.defaultKeyCode
    return when (slot) {
        GamepadActionSlot.CONFIRM -> GamepadKeyCodes.BUTTON_B
        GamepadActionSlot.BACK -> GamepadKeyCodes.BUTTON_A
        else -> slot.defaultKeyCode
    }
}

fun resolveBoundKeyCode(
    config: GamepadConfig,
    slot: GamepadActionSlot,
    effectiveProfile: GamepadControllerProfile,
): Int {
    val override = config.customBindings[slot.id]
    if (override != null) return override
    val swap = resolveEffectiveSwapConfirmBack(config, effectiveProfile)
    return defaultKeyCodeForSlot(slot, swap)
}

fun isSlotCustomized(
    config: GamepadConfig,
    slot: GamepadActionSlot,
    effectiveProfile: GamepadControllerProfile,
): Boolean {
    val swap = resolveEffectiveSwapConfirmBack(config, effectiveProfile)
    return resolveBoundKeyCode(config, slot, effectiveProfile) != defaultKeyCodeForSlot(slot, swap)
}

fun gamepadScopesConflict(a: GamepadActionScope, b: GamepadActionScope): Boolean {
    return a == GamepadActionScope.GLOBAL || b == GamepadActionScope.GLOBAL || a == b
}

fun findConflictingSlot(
    config: GamepadConfig,
    targetSlot: GamepadActionSlot,
    keyCode: Int,
    effectiveProfile: GamepadControllerProfile,
): GamepadActionSlot? {
    if (keyCode == GamepadKeyCodes.UNBOUND) return null
    return GamepadActionSlot.entries.firstOrNull { candidate ->
        candidate != targetSlot &&
            gamepadScopesConflict(candidate.scope, targetSlot.scope) &&
            resolveBoundKeyCode(config, candidate, effectiveProfile) == keyCode
    }
}

/**
 * 为指定动作绑定新按键；若同作用域内已有冲突动作，则自动互换两者的按键。
 */
fun applyCustomBinding(
    config: GamepadConfig,
    targetSlot: GamepadActionSlot,
    newKeyCode: Int,
    effectiveProfile: GamepadControllerProfile,
): GamepadConfig {
    if (!targetSlot.allowUnbind && newKeyCode == GamepadKeyCodes.UNBOUND) return config
    val swap = resolveEffectiveSwapConfirmBack(config, effectiveProfile)
    val previousKey = resolveBoundKeyCode(config, targetSlot, effectiveProfile)
    val conflicting = findConflictingSlot(config, targetSlot, newKeyCode, effectiveProfile)

    val nextMap = config.customBindings.toMutableMap()
    if (conflicting != null) {
        val replacementForConflict = if (previousKey == GamepadKeyCodes.UNBOUND && !conflicting.allowUnbind) {
            defaultKeyCodeForSlot(conflicting, swap)
        } else {
            previousKey
        }
        val conflictDefault = defaultKeyCodeForSlot(conflicting, swap)
        if (replacementForConflict == conflictDefault) {
            nextMap.remove(conflicting.id)
        } else {
            nextMap[conflicting.id] = replacementForConflict
        }
    }

    val targetDefault = defaultKeyCodeForSlot(targetSlot, swap)
    if (newKeyCode == targetDefault) {
        nextMap.remove(targetSlot.id)
    } else {
        nextMap[targetSlot.id] = newKeyCode
    }
    return config.copy(customBindings = nextMap)
}

fun resetCustomBinding(
    config: GamepadConfig,
    slot: GamepadActionSlot,
): GamepadConfig {
    if (slot.id !in config.customBindings) return config
    return config.copy(customBindings = config.customBindings - slot.id)
}

fun clearCustomBinding(
    config: GamepadConfig,
    slot: GamepadActionSlot,
    effectiveProfile: GamepadControllerProfile,
): GamepadConfig {
    if (!slot.allowUnbind) return config
    return applyCustomBinding(config, slot, GamepadKeyCodes.UNBOUND, effectiveProfile)
}

/** 将物理按键解析为当前场景下对应的功能槽位（优先匹配当前场景，再匹配全局）。 */
fun resolveActionSlotForKey(
    config: GamepadConfig,
    keyCode: Int,
    inVideo: Boolean,
    effectiveProfile: GamepadControllerProfile,
): GamepadActionSlot? {
    if (keyCode == GamepadKeyCodes.UNBOUND) return null
    val activeScope = if (inVideo) GamepadActionScope.PLAYER else GamepadActionScope.BROWSING
    val sceneMatch = GamepadActionSlot.entries.firstOrNull { slot ->
        slot.scope == activeScope && resolveBoundKeyCode(config, slot, effectiveProfile) == keyCode
    }
    if (sceneMatch != null) return sceneMatch
    return GamepadActionSlot.entries.firstOrNull { slot ->
        slot.scope == GamepadActionScope.GLOBAL && resolveBoundKeyCode(config, slot, effectiveProfile) == keyCode
    }
}

/** 紧凑按键徽章文本，用于底部 HUD 和快捷标签（自动适配 Xbox / PS / Switch 图标风格）。 */
fun formatGamepadKeyBadge(
    keyCode: Int,
    profile: GamepadControllerProfile,
): String {
    if (keyCode == GamepadKeyCodes.UNBOUND) return "未绑定"
    return when (profile) {
        GamepadControllerProfile.PLAYSTATION -> when (keyCode) {
            GamepadKeyCodes.BUTTON_A -> "✕"
            GamepadKeyCodes.BUTTON_B -> "○"
            GamepadKeyCodes.BUTTON_X -> "□"
            GamepadKeyCodes.BUTTON_Y -> "△"
            GamepadKeyCodes.BUTTON_L1 -> "L1"
            GamepadKeyCodes.BUTTON_R1 -> "R1"
            GamepadKeyCodes.BUTTON_L2 -> "L2"
            GamepadKeyCodes.BUTTON_R2 -> "R2"
            GamepadKeyCodes.BUTTON_SELECT -> "Create"
            GamepadKeyCodes.BUTTON_START -> "Options"
            GamepadKeyCodes.BUTTON_THUMBL -> "L3"
            GamepadKeyCodes.BUTTON_THUMBR -> "R3"
            GamepadKeyCodes.BUTTON_14 -> "触控板"
            else -> formatGenericExtraButton(keyCode)
        }
        GamepadControllerProfile.SWITCH -> when (keyCode) {
            GamepadKeyCodes.BUTTON_A -> "B"
            GamepadKeyCodes.BUTTON_B -> "A"
            GamepadKeyCodes.BUTTON_X -> "Y"
            GamepadKeyCodes.BUTTON_Y -> "X"
            GamepadKeyCodes.BUTTON_L1 -> "L"
            GamepadKeyCodes.BUTTON_R1 -> "R"
            GamepadKeyCodes.BUTTON_L2 -> "ZL"
            GamepadKeyCodes.BUTTON_R2 -> "ZR"
            GamepadKeyCodes.BUTTON_SELECT -> "-"
            GamepadKeyCodes.BUTTON_START -> "+"
            GamepadKeyCodes.BUTTON_THUMBL -> "L3"
            GamepadKeyCodes.BUTTON_THUMBR -> "R3"
            else -> formatGenericExtraButton(keyCode)
        }
        GamepadControllerProfile.XBOX,
        GamepadControllerProfile.GENERIC,
        GamepadControllerProfile.AUTO -> when (keyCode) {
            GamepadKeyCodes.BUTTON_A -> "A"
            GamepadKeyCodes.BUTTON_B -> "B"
            GamepadKeyCodes.BUTTON_X -> "X"
            GamepadKeyCodes.BUTTON_Y -> "Y"
            GamepadKeyCodes.BUTTON_L1 -> "LB"
            GamepadKeyCodes.BUTTON_R1 -> "RB"
            GamepadKeyCodes.BUTTON_L2 -> "LT"
            GamepadKeyCodes.BUTTON_R2 -> "RT"
            GamepadKeyCodes.BUTTON_SELECT -> "View"
            GamepadKeyCodes.BUTTON_START -> "Menu"
            GamepadKeyCodes.BUTTON_THUMBL -> "L3"
            GamepadKeyCodes.BUTTON_THUMBR -> "R3"
            else -> formatGenericExtraButton(keyCode)
        }
    }
}

/** 详细按键名称，用于设置页、按键录制弹窗与实时测试仪。 */
fun formatGamepadKeyDisplayName(
    keyCode: Int,
    profile: GamepadControllerProfile,
): String {
    if (keyCode == GamepadKeyCodes.UNBOUND) return "未绑定"
    return when (profile) {
        GamepadControllerProfile.PLAYSTATION -> when (keyCode) {
            GamepadKeyCodes.BUTTON_A -> "✕ 键"
            GamepadKeyCodes.BUTTON_B -> "○ 键"
            GamepadKeyCodes.BUTTON_X -> "□ 键"
            GamepadKeyCodes.BUTTON_Y -> "△ 键"
            GamepadKeyCodes.BUTTON_L1 -> "L1 肩键"
            GamepadKeyCodes.BUTTON_R1 -> "R1 肩键"
            GamepadKeyCodes.BUTTON_L2 -> "L2 扳机"
            GamepadKeyCodes.BUTTON_R2 -> "R2 扳机"
            GamepadKeyCodes.BUTTON_SELECT -> "Create / Share"
            GamepadKeyCodes.BUTTON_START -> "Options"
            GamepadKeyCodes.BUTTON_THUMBL -> "L3（左摇杆按压）"
            GamepadKeyCodes.BUTTON_THUMBR -> "R3（右摇杆按压）"
            GamepadKeyCodes.BUTTON_14 -> "触控板按键"
            else -> formatGenericExtraButton(keyCode)
        }
        GamepadControllerProfile.SWITCH -> when (keyCode) {
            GamepadKeyCodes.BUTTON_A -> "B 键（下）"
            GamepadKeyCodes.BUTTON_B -> "A 键（右）"
            GamepadKeyCodes.BUTTON_X -> "Y 键（左）"
            GamepadKeyCodes.BUTTON_Y -> "X 键（上）"
            GamepadKeyCodes.BUTTON_L1 -> "L 肩键"
            GamepadKeyCodes.BUTTON_R1 -> "R 肩键"
            GamepadKeyCodes.BUTTON_L2 -> "ZL 扳机"
            GamepadKeyCodes.BUTTON_R2 -> "ZR 扳机"
            GamepadKeyCodes.BUTTON_SELECT -> "-（减号键）"
            GamepadKeyCodes.BUTTON_START -> "+（加号键）"
            GamepadKeyCodes.BUTTON_THUMBL -> "L3（左摇杆按压）"
            GamepadKeyCodes.BUTTON_THUMBR -> "R3（右摇杆按压）"
            else -> formatGenericExtraButton(keyCode)
        }
        GamepadControllerProfile.XBOX,
        GamepadControllerProfile.GENERIC,
        GamepadControllerProfile.AUTO -> when (keyCode) {
            GamepadKeyCodes.BUTTON_A -> "A 键"
            GamepadKeyCodes.BUTTON_B -> "B 键"
            GamepadKeyCodes.BUTTON_X -> "X 键"
            GamepadKeyCodes.BUTTON_Y -> "Y 键"
            GamepadKeyCodes.BUTTON_L1 -> "LB 肩键"
            GamepadKeyCodes.BUTTON_R1 -> "RB 肩键"
            GamepadKeyCodes.BUTTON_L2 -> "LT 扳机"
            GamepadKeyCodes.BUTTON_R2 -> "RT 扳机"
            GamepadKeyCodes.BUTTON_SELECT -> "View / Back"
            GamepadKeyCodes.BUTTON_START -> "Menu / Start"
            GamepadKeyCodes.BUTTON_THUMBL -> "L3（左摇杆按压）"
            GamepadKeyCodes.BUTTON_THUMBR -> "R3（右摇杆按压）"
            else -> formatGenericExtraButton(keyCode)
        }
    }
}

fun formatTriggerPairLabels(profile: GamepadControllerProfile): Pair<String, String> = when (profile) {
    GamepadControllerProfile.PLAYSTATION -> "L2" to "R2"
    GamepadControllerProfile.SWITCH -> "ZL" to "ZR"
    GamepadControllerProfile.XBOX,
    GamepadControllerProfile.GENERIC,
    GamepadControllerProfile.AUTO -> "LT" to "RT"
}

private fun formatGenericExtraButton(keyCode: Int): String = when (keyCode) {
    GamepadKeyCodes.BUTTON_C -> "C 键"
    GamepadKeyCodes.BUTTON_Z -> "Z 键"
    GamepadKeyCodes.BUTTON_MODE -> "Home / Mode"
    in GamepadKeyCodes.BUTTON_1..GamepadKeyCodes.BUTTON_16 ->
        "扩展键 ${keyCode - GamepadKeyCodes.BUTTON_1 + 1}"
    else -> "按键 #$keyCode"
}

/**
 * 对双轴摇杆应用径向死区与非线性响应曲线：
 * - 死区内严格归零，防止手柄漂移；
 * - 越过死区后从 0 平滑起跳，消除阶跃感；
 * - 幂曲线兼顾小幅微调精度与大屏快速跨屏。
 */
internal fun normalizeRadialStick(
    rawX: Float,
    rawY: Float,
    deadzone: Float,
    curveExponent: Float = 1.8f,
): NormalizedStickVector {
    val safeDeadzone = deadzone.coerceIn(0.05f, 0.45f)
    val clampedX = rawX.coerceIn(-1f, 1f)
    val clampedY = rawY.coerceIn(-1f, 1f)
    val magnitude = hypot(clampedX.toDouble(), clampedY.toDouble()).toFloat()
    if (magnitude <= safeDeadzone || magnitude <= 1e-5f) {
        return NormalizedStickVector()
    }
    val rescaled = ((magnitude - safeDeadzone) / (1f - safeDeadzone)).coerceIn(0f, 1f)
    val curved = rescaled.pow(curveExponent)
    val scale = curved / magnitude
    return NormalizedStickVector(
        x = (clampedX * scale).coerceIn(-1f, 1f),
        y = (clampedY * scale).coerceIn(-1f, 1f),
    )
}

/** 计算单帧光标位移（像素），按住左扳机时自动切入慢速精准瞄准。 */
internal fun computeCursorStepPx(
    vector: NormalizedStickVector,
    deltaSeconds: Float,
    density: Float,
    sensitivity: Float,
    precisionAimHeld: Boolean,
): Pair<Float, Float> {
    if (!vector.isActive || deltaSeconds <= 0f) return 0f to 0f
    val clampedDt = deltaSeconds.coerceAtMost(0.05f)
    val baseDpPerSec = 680f * sensitivity.coerceIn(0.4f, 3.0f)
    val precisionFactor = if (precisionAimHeld) 0.35f else 1.0f
    val pxPerSec = baseDpPerSec * density.coerceAtLeast(1f) * precisionFactor
    return (vector.x * pxPerSec * clampedDt) to (vector.y * pxPerSec * clampedDt)
}

/**
 * 右摇杆滚动主轴锁定（带滞回）：
 * 上下滚动时屏蔽微小的左右偏移，避免唤醒 HorizontalPager / 横向标签栏导致卡顿或误切页；
 * 左右推动时屏蔽微小上下偏移，便于在横向卡片行或标签栏精准横滑。
 */
internal fun resolveLockedScrollVector(
    rawX: Float,
    rawY: Float,
    currentAxis: GamepadScrollAxis?,
): Pair<GamepadScrollAxis?, NormalizedStickVector> {
    val absX = abs(rawX)
    val absY = abs(rawY)
    if (absX < 1e-4f && absY < 1e-4f) {
        return null to NormalizedStickVector()
    }
    val nextAxis = when (currentAxis) {
        GamepadScrollAxis.VERTICAL ->
            if (absX > absY * 2.1f && absX > 0.25f) GamepadScrollAxis.HORIZONTAL else GamepadScrollAxis.VERTICAL
        GamepadScrollAxis.HORIZONTAL ->
            if (absY > absX * 2.1f && absY > 0.25f) GamepadScrollAxis.VERTICAL else GamepadScrollAxis.HORIZONTAL
        null ->
            if (absY >= absX * 0.85f) GamepadScrollAxis.VERTICAL else GamepadScrollAxis.HORIZONTAL
    }
    val locked = when (nextAxis) {
        GamepadScrollAxis.VERTICAL -> NormalizedStickVector(x = 0f, y = rawY)
        GamepadScrollAxis.HORIZONTAL -> NormalizedStickVector(x = rawX, y = 0f)
    }
    return nextAxis to locked
}

/**
 * 计算单帧连续触控拖拽滚动位移（像素）：
 * 默认向下推右摇杆（y > 0）→ 手指向上拖（dy < 0）→ 列表向下滚动，贴合直觉且与原生手势一致。
 */
internal fun computeScrollDragStepPx(
    lockedVector: NormalizedStickVector,
    deltaSeconds: Float,
    density: Float,
    scrollSensitivity: Float,
    invertVertical: Boolean,
): Pair<Float, Float> {
    if (!lockedVector.isActive || deltaSeconds <= 0f) return 0f to 0f
    val clampedDt = deltaSeconds.coerceAtMost(0.05f)
    val safeDensity = density.coerceAtLeast(1f)
    val safeSensitivity = scrollSensitivity.coerceIn(0.4f, 3.0f)
    val verticalSign = if (invertVertical) 1f else -1f
    val dxPx = if (lockedVector.x == 0f) 0f else -lockedVector.x * safeSensitivity * 680f * safeDensity * clampedDt
    val dyPx = if (lockedVector.y == 0f) 0f else lockedVector.y * verticalSign * safeSensitivity * 820f * safeDensity * clampedDt
    return dxPx to dyPx
}

/**
 * 将任意量程的模拟扳机轴归一化到 [0f, 1f]：
 * 兼容 Xbox（0..1）与 PlayStation DualShock 4 / DualSense 蓝牙（-1..1，松开为 -1）。
 */
internal fun normalizeAxisToUnitRange(
    value: Float,
    min: Float,
    max: Float,
): Float {
    val span = max - min
    if (span <= 1e-5f) return value.coerceIn(0f, 1f)
    return ((value - min) / span).coerceIn(0f, 1f)
}

/** 兼容不同 Xbox / PS / 通用手柄驱动上报的扳机轴。 */
internal fun resolveTriggerValue(primaryAxis: Float, fallbackAxis: Float): Float {
    return max(primaryAxis.coerceIn(0f, 1f), fallbackAxis.coerceIn(0f, 1f))
}

/** 综合模拟扳机轴与数字扳机按键（如 Switch Pro ZL/ZR）。 */
internal fun resolveMultiControllerTriggerValue(
    analogValue: Float,
    digitalPressed: Boolean,
): Float {
    return if (digitalPressed) 1f else analogValue.coerceIn(0f, 1f)
}

/** 带滞回的扳机数字开关判定，避免临界抖动反复触发。 */
internal fun resolveTriggerPressed(
    triggerValue: Float,
    currentlyPressed: Boolean,
    pressThreshold: Float = 0.55f,
    releaseThreshold: Float = 0.30f,
): Boolean {
    return if (currentlyPressed) {
        triggerValue >= releaseThreshold
    } else {
        triggerValue >= pressThreshold
    }
}

/**
 * 将连续的 HAT 轴或摇杆方向转换为带长按连发的离散方向脉冲。
 * 供蓝牙 Xbox / PS / Switch 十字键（AXIS_HAT_X / AXIS_HAT_Y）和焦点模式左摇杆共用。
 */
internal class DirectionalRepeatTracker(
    private val initialDelayMs: Long = 320L,
    private val repeatIntervalMs: Long = 95L,
) {
    private var activeDirection: GamepadDirection? = null
    private var nextEmitAtMs: Long = 0L

    fun update(direction: GamepadDirection?, nowMs: Long): GamepadDirection? {
        if (direction == null) {
            activeDirection = null
            nextEmitAtMs = 0L
            return null
        }
        if (direction != activeDirection) {
            activeDirection = direction
            nextEmitAtMs = nowMs + initialDelayMs
            return direction
        }
        if (nowMs >= nextEmitAtMs) {
            nextEmitAtMs = nowMs + repeatIntervalMs
            return direction
        }
        return null
    }

    fun reset() {
        activeDirection = null
        nextEmitAtMs = 0L
    }
}

/** 将十字键 HAT 轴或摇杆二维值提取为主方向。 */
internal fun resolveDominantDirection(
    x: Float,
    y: Float,
    threshold: Float = 0.5f,
): GamepadDirection? {
    val absX = abs(x)
    val absY = abs(y)
    if (absX < threshold && absY < threshold) return null
    return if (absX >= absY) {
        if (x < 0f) GamepadDirection.LEFT else GamepadDirection.RIGHT
    } else {
        if (y < 0f) GamepadDirection.UP else GamepadDirection.DOWN
    }
}

sealed interface GamepadNavigationAction {
    data class SwitchBottomTabDelta(val delta: Int) : GamepadNavigationAction
    data object ReselectCurrentTab : GamepadNavigationAction
    data object OpenSearch : GamepadNavigationAction
    data object OpenSettings : GamepadNavigationAction
}

sealed interface GamepadPlayerAction {
    data object PlayPause : GamepadPlayerAction
    data class SeekRelative(val deltaMs: Long) : GamepadPlayerAction
    data class AdjustVolume(val direction: Int) : GamepadPlayerAction
    data class AdjustBrightness(val deltaFraction: Float) : GamepadPlayerAction
    data object ToggleMute : GamepadPlayerAction
    data object ToggleDanmaku : GamepadPlayerAction
    data object ToggleFullscreen : GamepadPlayerAction
    data object ToggleLike : GamepadPlayerAction
    data object TripleAction : GamepadPlayerAction
    data object ToggleScreenLock : GamepadPlayerAction
    data object PreviousPart : GamepadPlayerAction
    data object NextPart : GamepadPlayerAction
    data object ToggleControls : GamepadPlayerAction
    data class SpeedBoostChanged(val active: Boolean, val speed: Float) : GamepadPlayerAction
}
