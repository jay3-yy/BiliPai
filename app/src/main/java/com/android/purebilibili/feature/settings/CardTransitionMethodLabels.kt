package com.android.purebilibili.feature.settings

import androidx.annotation.StringRes
import com.android.purebilibili.R
import com.android.purebilibili.core.store.CardTransitionMethod

internal const val CARD_TRANSITION_METHOD_SETTING_ID = "animation.card_transition_method"

@StringRes
internal fun cardTransitionMethodLabel(method: CardTransitionMethod): Int = when (method) {
    CardTransitionMethod.AUTO -> R.string.animation_card_transition_method_auto
    CardTransitionMethod.OFFICIAL -> R.string.animation_card_transition_method_official
    CardTransitionMethod.OPLUS_LEGACY -> R.string.animation_card_transition_method_oplus
    CardTransitionMethod.VIVO_ONESHOT -> R.string.animation_card_transition_method_vivo
    CardTransitionMethod.XIAOMI_ROUNDED -> R.string.animation_card_transition_method_xiaomi
    CardTransitionMethod.ANDROID_SHARED -> R.string.animation_card_transition_method_android_shared
    CardTransitionMethod.STANDARD -> R.string.animation_card_transition_method_standard
}
