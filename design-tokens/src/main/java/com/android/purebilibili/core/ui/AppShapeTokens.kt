package com.android.purebilibili.core.ui

import androidx.compose.ui.unit.dp

/** Semantic container categories shared by the Material 3 and MIUIX themes. */
enum class ContainerLevel {
    /** Progress tracks / hairline chips. base = 1.5dp. */
    Micro,
    /** Tiny tags / badges. base = 4dp. */
    Tag,
    /** Small chips / micro-buttons. base = 6dp. */
    Chip,
    /** Input fields, search bars, small chip-like containers. base = 10dp. */
    Field,
    /** Standard surface cards. base = 12dp. */
    Card,
    /** Dense media cover; retains the legacy Card geometry outside non-glass MIUIX. */
    MediaCover,
    /** Prominent media / hero cards with a full large-radius outline. base = 20dp. */
    ProminentCard,
    /** Alert / confirm dialog containers. base = 14dp. */
    Dialog,
    /** Bottom sheet / modal sheet (top-rounded). base = 20dp. */
    Sheet,
    /** Floating elements — FABs, floating bars. base = 28dp. */
    Floating,
    /** Pill / segmented selectors — radius comes from chrome tokens directly. */
    Pill
}

/** Pure geometry shared by phone theme adapters and TV. */
object AppShapeTokens {
    const val MaterialCornerRadiusScale = 0.9f
    const val MiuixCornerRadiusScale = 1.15f

    val MaterialExtraSmall = 4.dp
    val MaterialSmall = 8.dp
    val MaterialMedium = 12.dp
    val MaterialLarge = 16.dp
    val MaterialExtraLarge = 28.dp

    fun baseCornerDp(level: ContainerLevel): Float = when (level) {
        ContainerLevel.Micro -> 1.5f
        ContainerLevel.Tag -> 4f
        ContainerLevel.Chip -> 6f
        ContainerLevel.Field -> 10f
        ContainerLevel.Card -> 12f
        ContainerLevel.MediaCover -> 12f
        ContainerLevel.ProminentCard -> 20f
        ContainerLevel.Dialog -> 14f
        ContainerLevel.Sheet -> 20f
        ContainerLevel.Floating -> 28f
        ContainerLevel.Pill -> 0f
    }
}
