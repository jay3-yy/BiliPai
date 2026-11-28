package com.android.purebilibili.feature.video.ui.overlay

internal const val WEAK_NETWORK_STRICTNESS_MIN = -2
internal const val WEAK_NETWORK_STRICTNESS_MAX = 6
internal const val WEAK_NETWORK_MANUAL_DOWNGRADE_WINDOW_MS = 20_000L

internal enum class WeakNetworkBucket {
    WIFI,
    CELLULAR,
}

internal enum class WeakNetworkPromptOutcome {
    ACCEPTED,
    MANUAL_DOWNGRADE_DURING_STALL,
    DISMISSED,
    AUTO_DISMISSED_IGNORED,
}

internal data class WeakNetworkLearningState(
    val strictnessScore: Int = 0,
    val consecutiveNegativeCount: Int = 0,
    val acceptedCount: Int = 0,
    val dismissedCount: Int = 0,
    val ignoredCount: Int = 0,
    val cooldownUntilEpochMs: Long = 0L,
) {
    val totalFeedbackCount: Int
        get() = acceptedCount + dismissedCount + ignoredCount
}

internal data class WeakNetworkAdaptiveState(
    val wifi: WeakNetworkLearningState = WeakNetworkLearningState(),
    val cellular: WeakNetworkLearningState = WeakNetworkLearningState(),
) {
    val hasLearnedFeedback: Boolean
        get() = wifi.totalFeedbackCount > 0 || cellular.totalFeedbackCount > 0

    fun stateFor(bucket: WeakNetworkBucket): WeakNetworkLearningState = when (bucket) {
        WeakNetworkBucket.WIFI -> wifi
        WeakNetworkBucket.CELLULAR -> cellular
    }

    fun recordOutcome(
        bucket: WeakNetworkBucket,
        outcome: WeakNetworkPromptOutcome,
        nowEpochMs: Long,
    ): WeakNetworkAdaptiveState = when (bucket) {
        WeakNetworkBucket.WIFI -> copy(
            wifi = recordWeakNetworkPromptOutcome(wifi, outcome, nowEpochMs),
        )
        WeakNetworkBucket.CELLULAR -> copy(
            cellular = recordWeakNetworkPromptOutcome(cellular, outcome, nowEpochMs),
        )
    }
}

internal data class WeakNetworkPromptProfile(
    val minEffectiveStallMs: Long,
    val singleStallThresholdMs: Long,
    val rebufferWindowMs: Long,
    val rebufferCountThreshold: Int,
    val rebufferMinTotalDurationMs: Long,
    val summaryLabel: String,
)

internal fun resolveWeakNetworkPromptProfile(
    state: WeakNetworkLearningState,
): WeakNetworkPromptProfile {
    val clamped = state.strictnessScore.coerceIn(
        WEAK_NETWORK_STRICTNESS_MIN,
        WEAK_NETWORK_STRICTNESS_MAX,
    )
    return when {
        clamped <= -2 -> WeakNetworkPromptProfile(
            minEffectiveStallMs = 1_000L,
            singleStallThresholdMs = 5_500L,
            rebufferWindowMs = 45_000L,
            rebufferCountThreshold = 2,
            rebufferMinTotalDurationMs = 3_200L,
            summaryLabel = "更灵敏",
        )
        clamped == -1 -> WeakNetworkPromptProfile(
            minEffectiveStallMs = 1_100L,
            singleStallThresholdMs = 6_500L,
            rebufferWindowMs = 45_000L,
            rebufferCountThreshold = 3,
            rebufferMinTotalDurationMs = 3_800L,
            summaryLabel = "略灵敏",
        )
        clamped == 0 -> WeakNetworkPromptProfile(
            minEffectiveStallMs = WEAK_NETWORK_MIN_EFFECTIVE_STALL_MS,
            singleStallThresholdMs = WEAK_NETWORK_SINGLE_STALL_MS,
            rebufferWindowMs = WEAK_NETWORK_REBUFFER_WINDOW_MS,
            rebufferCountThreshold = WEAK_NETWORK_REBUFFER_COUNT_THRESHOLD,
            rebufferMinTotalDurationMs = WEAK_NETWORK_REBUFFER_MIN_TOTAL_DURATION_MS,
            summaryLabel = "标准",
        )
        clamped <= 2 -> WeakNetworkPromptProfile(
            minEffectiveStallMs = 1_500L,
            singleStallThresholdMs = 9_500L,
            rebufferWindowMs = 45_000L,
            rebufferCountThreshold = 3,
            rebufferMinTotalDurationMs = 6_000L,
            summaryLabel = "偏克制",
        )
        clamped <= 4 -> WeakNetworkPromptProfile(
            minEffectiveStallMs = 1_800L,
            singleStallThresholdMs = 12_000L,
            rebufferWindowMs = 50_000L,
            rebufferCountThreshold = 4,
            rebufferMinTotalDurationMs = 8_000L,
            summaryLabel = "较克制",
        )
        else -> WeakNetworkPromptProfile(
            minEffectiveStallMs = 2_200L,
            singleStallThresholdMs = 15_000L,
            rebufferWindowMs = 60_000L,
            rebufferCountThreshold = 5,
            rebufferMinTotalDurationMs = 11_000L,
            summaryLabel = "极少打扰",
        )
    }
}

internal fun isWeakNetworkPromptInCooldown(
    state: WeakNetworkLearningState,
    nowEpochMs: Long,
): Boolean = state.cooldownUntilEpochMs > nowEpochMs

internal fun recordWeakNetworkPromptOutcome(
    state: WeakNetworkLearningState,
    outcome: WeakNetworkPromptOutcome,
    nowEpochMs: Long,
): WeakNetworkLearningState = when (outcome) {
    WeakNetworkPromptOutcome.ACCEPTED -> state.copy(
        strictnessScore = (state.strictnessScore - 2).coerceIn(
            WEAK_NETWORK_STRICTNESS_MIN,
            WEAK_NETWORK_STRICTNESS_MAX,
        ),
        consecutiveNegativeCount = 0,
        acceptedCount = state.acceptedCount + 1,
        cooldownUntilEpochMs = 0L,
    )

    WeakNetworkPromptOutcome.MANUAL_DOWNGRADE_DURING_STALL -> state.copy(
        strictnessScore = (state.strictnessScore - 1).coerceIn(
            WEAK_NETWORK_STRICTNESS_MIN,
            WEAK_NETWORK_STRICTNESS_MAX,
        ),
        consecutiveNegativeCount = 0,
        acceptedCount = state.acceptedCount + 1,
        cooldownUntilEpochMs = 0L,
    )

    WeakNetworkPromptOutcome.DISMISSED -> {
        val nextNegative = (state.consecutiveNegativeCount + 1).coerceAtMost(5)
        val cooldownMs = when (nextNegative) {
            1 -> 10 * 60_000L
            2 -> 30 * 60_000L
            3 -> 60 * 60_000L
            else -> 120 * 60_000L
        }
        state.copy(
            strictnessScore = (state.strictnessScore + 2).coerceIn(
                WEAK_NETWORK_STRICTNESS_MIN,
                WEAK_NETWORK_STRICTNESS_MAX,
            ),
            consecutiveNegativeCount = nextNegative,
            dismissedCount = state.dismissedCount + 1,
            cooldownUntilEpochMs = nowEpochMs + cooldownMs,
        )
    }

    WeakNetworkPromptOutcome.AUTO_DISMISSED_IGNORED -> {
        val nextNegative = (state.consecutiveNegativeCount + 1).coerceAtMost(5)
        val cooldownMs = when (nextNegative) {
            1 -> 5 * 60_000L
            2 -> 15 * 60_000L
            3 -> 30 * 60_000L
            else -> 60 * 60_000L
        }
        state.copy(
            strictnessScore = (state.strictnessScore + 1).coerceIn(
                WEAK_NETWORK_STRICTNESS_MIN,
                WEAK_NETWORK_STRICTNESS_MAX,
            ),
            consecutiveNegativeCount = nextNegative,
            ignoredCount = state.ignoredCount + 1,
            cooldownUntilEpochMs = nowEpochMs + cooldownMs,
        )
    }
}

internal fun resolveWeakNetworkDegradeSettingSubtitle(
    enabled: Boolean,
    state: WeakNetworkLearningState,
): String {
    if (!enabled) {
        return "已关闭，播放卡顿时不再弹出降画质提示"
    }
    if (state.totalFeedbackCount <= 0) {
        return "连续卡顿或长时间缓冲时提示切换画质，并根据你的操作自动调整灵敏度"
    }
    val profile = resolveWeakNetworkPromptProfile(state)
    return "连续卡顿或长时间缓冲时提示切换画质；当前习惯：${profile.summaryLabel}"
}

internal fun resolveWeakNetworkDegradeSettingSubtitle(
    enabled: Boolean,
    state: WeakNetworkAdaptiveState,
): String {
    if (!enabled) {
        return "已关闭，播放卡顿时不再弹出降画质提示"
    }
    if (!state.hasLearnedFeedback) {
        return "连续卡顿或长时间缓冲时提示切换画质，并按 Wi-Fi 与移动网络分别学习灵敏度"
    }
    val parts = buildList {
        if (state.wifi.totalFeedbackCount > 0) {
            add("Wi-Fi：${resolveWeakNetworkPromptProfile(state.wifi).summaryLabel}")
        }
        if (state.cellular.totalFeedbackCount > 0) {
            add("移动网络：${resolveWeakNetworkPromptProfile(state.cellular).summaryLabel}")
        }
    }
    return "连续卡顿或长时间缓冲时提示切换画质；${parts.joinToString(" · ")}"
}
