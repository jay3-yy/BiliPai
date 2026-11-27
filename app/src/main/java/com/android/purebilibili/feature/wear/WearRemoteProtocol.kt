package com.android.purebilibili.feature.wear

import org.json.JSONObject

internal const val WEAR_PATH_COMMAND = "/bilipai/wear/command"
internal const val WEAR_PATH_STATE = "/bilipai/wear/state"
internal const val WEAR_SEEK_STEP_MS = 15_000L

internal val WEAR_SPEED_STEPS = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f)

internal enum class WearRemoteCommand(val wireValue: String) {
    TOGGLE_PLAY("toggle_play"),
    SEEK_FORWARD_15S("seek_forward_15s"),
    SEEK_BACKWARD_15S("seek_backward_15s"),
    NEXT_ITEM("next_item"),
    PREVIOUS_ITEM("previous_item"),
    CYCLE_SPEED("cycle_speed"),
    REQUEST_STATE("request_state");

    companion object {
        fun fromWire(raw: String?): WearRemoteCommand? =
            entries.firstOrNull { it.wireValue == raw?.trim() }
    }
}

internal data class WearPlaybackStateSnapshot(
    val title: String = "",
    val author: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val hasActiveMedia: Boolean = false,
    val locked: Boolean = false,
) {
    fun toJsonBytes(): ByteArray = JSONObject()
        .put("title", title)
        .put("author", author)
        .put("playing", playing)
        .put("positionMs", positionMs.coerceAtLeast(0L))
        .put("durationMs", durationMs.coerceAtLeast(0L))
        .put("speed", speed.toDouble())
        .put("canPrevious", canPrevious)
        .put("canNext", canNext)
        .put("hasActiveMedia", hasActiveMedia)
        .put("locked", locked)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        fun fromJsonBytes(bytes: ByteArray): WearPlaybackStateSnapshot? = runCatching {
            val obj = JSONObject(bytes.toString(Charsets.UTF_8))
            WearPlaybackStateSnapshot(
                title = obj.optString("title", ""),
                author = obj.optString("author", ""),
                playing = obj.optBoolean("playing", false),
                positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                speed = obj.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4f),
                canPrevious = obj.optBoolean("canPrevious", false),
                canNext = obj.optBoolean("canNext", false),
                hasActiveMedia = obj.optBoolean("hasActiveMedia", false),
                locked = obj.optBoolean("locked", false),
            )
        }.getOrNull()
    }
}

internal fun resolveNextWearPlaybackSpeed(currentSpeed: Float): Float {
    val currentIndex = WEAR_SPEED_STEPS.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.05f }
    val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % WEAR_SPEED_STEPS.size
    return WEAR_SPEED_STEPS[nextIndex]
}
