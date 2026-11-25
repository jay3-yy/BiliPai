package com.android.purebilibili.core.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lightweight in-app hint bus for plugin effect visualization.
 *
 * Hints are coalesced per plugin id with a cooldown so high-frequency
 * filters (feed/danmaku) only surface a brief native chip.
 */
object PluginEffectHintBus {

    @Volatile
    private var effectMatchHintsEnabled = false

    private val _current = MutableStateFlow<PluginEffectHint?>(null)
    val current: StateFlow<PluginEffectHint?> = _current.asStateFlow()

    private val lastAcceptedAtMs = mutableMapOf<String, Long>()

    fun tryEmit(
        hint: PluginEffectHint?,
        nowMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = PLUGIN_EFFECT_HINT_VISIBLE_MS
    ) {
        if (hint == null) return
        if (!shouldShowPluginEffectHint(hint.kind, effectMatchHintsEnabled)) return
        synchronized(lastAcceptedAtMs) {
            val lastAccepted = lastAcceptedAtMs[hint.pluginId]
            if (!shouldAcceptPluginEffectHint(lastAccepted, nowMs, cooldownMs)) return
            lastAcceptedAtMs[hint.pluginId] = nowMs
            _current.value = hint.copy(issuedAtMs = nowMs)
        }
    }

    fun setEffectMatchHintsEnabled(enabled: Boolean) {
        effectMatchHintsEnabled = enabled
        if (!enabled) {
            _current.update { current ->
                if (current != null && !shouldShowPluginEffectHint(current.kind, effectMatchHintsEnabled = false)) {
                    null
                } else {
                    current
                }
            }
        }
    }

    fun dismiss(issuedAtMs: Long) {
        _current.update { current ->
            if (current?.issuedAtMs == issuedAtMs) null else current
        }
    }

    fun clearForTests() {
        synchronized(lastAcceptedAtMs) {
            lastAcceptedAtMs.clear()
        }
        _current.value = null
        effectMatchHintsEnabled = false
    }
}
