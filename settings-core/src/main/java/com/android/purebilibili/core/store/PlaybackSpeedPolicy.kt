package com.android.purebilibili.core.store

import kotlin.math.abs
import kotlin.math.roundToInt

fun normalizePlaybackSpeed(speed: Float): Float {
    return speed.coerceIn(0.1f, 8.0f)
}

const val DEFAULT_LONG_PRESS_SPEED = 2.0f
const val LONG_PRESS_SPEED_MIN = 1.0f
const val LONG_PRESS_SPEED_MAX = 8.0f
const val LONG_PRESS_SPEED_STEP = 0.05f

// The initial list includes every speed previously offered by the video menus or long-press selector.
val DEFAULT_PLAYBACK_SPEED_OPTIONS =
    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.3f, 1.5f, 1.75f, 2f, 2.5f, 3f)

fun normalizePlaybackSpeedOptions(options: Collection<Float>): List<Float> =
    (options.asSequence()
        .filter { it.isFinite() && it in 0.1f..8f }
        .map { (it * 100f).roundToInt() / 100f } + sequenceOf(1f))
        .distinct()
        .sorted()
        .toList()

private fun parsePlaybackSpeedInput(input: String): Float? {
    val text = input.trim().removeSuffix("x")
    val decimalPoint = text.indexOf('.')
    if ((decimalPoint >= 0 && text.length - decimalPoint - 1 !in 1..2) ||
        text.any { !it.isDigit() && it != '.' }) return null
    val parsed = text.toFloatOrNull() ?: return null
    if (!parsed.isFinite() || parsed !in 0.1f..8f) return null
    val hundredths = (parsed * 100f).roundToInt()
    if (abs(parsed * 100f - hundredths) > 0.001f) return null
    return hundredths / 100f
}

/** Accept new menu entries with at most two decimals, within the player's 0.1–8x range. */
fun parseNewPlaybackSpeedOption(input: String, options: List<Float>): Float? =
    parsePlaybackSpeedInput(input)?.takeIf { it !in options }

/** Direct long-press entry is not restricted to the player's menu speeds. */
fun parseLongPressSpeedInput(input: String): Float? =
    parsePlaybackSpeedInput(input)?.takeIf { it >= LONG_PRESS_SPEED_MIN }

fun resolvePlaybackSpeedOptions(
    storedValues: String?,
    legacyDefaultSpeed: Float = 1f,
    legacyLastSpeed: Float = 1f
): List<Float> = if (storedValues == null) {
    normalizePlaybackSpeedOptions(
        DEFAULT_PLAYBACK_SPEED_OPTIONS + listOf(legacyDefaultSpeed, legacyLastSpeed)
    )
} else {
    normalizePlaybackSpeedOptions(storedValues.split(',').mapNotNull(String::toFloatOrNull))
}

fun nearestPlaybackSpeed(speed: Float, options: List<Float>, fallback: Float = 1f): Float {
    val target = if (speed.isFinite()) speed else fallback
    return options.minByOrNull { option -> abs(option - target) } ?: fallback
}

fun normalizeLongPressSpeed(speed: Float): Float {
    val validSpeed = if (speed.isFinite()) speed else DEFAULT_LONG_PRESS_SPEED
    return (validSpeed.coerceIn(LONG_PRESS_SPEED_MIN, LONG_PRESS_SPEED_MAX) * 100f).roundToInt() / 100f
}

fun resolvePreferredPlaybackSpeed(
    defaultSpeed: Float,
    rememberLastSpeed: Boolean,
    lastSpeed: Float
): Float {
    val normalizedDefault = normalizePlaybackSpeed(defaultSpeed)
    if (!rememberLastSpeed) return normalizedDefault
    return normalizePlaybackSpeed(lastSpeed)
}
