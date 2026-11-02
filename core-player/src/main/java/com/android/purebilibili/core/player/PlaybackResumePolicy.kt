package com.android.purebilibili.core.player

/** An explicit seek wins; watched-through automatic progress starts again from zero. */
fun resolvePlaybackResumePosition(explicitMs: Long?, localMs: Long, serverMs: Long?, durationMs: Long): Long {
    val candidate = explicitMs ?: serverMs ?: localMs
    if (durationMs <= 0) return candidate.coerceAtLeast(0)
    if (explicitMs == null && candidate >= durationMs * 0.95) return 0
    return candidate.coerceIn(0, (durationMs - 1_000).coerceAtLeast(0))
}
