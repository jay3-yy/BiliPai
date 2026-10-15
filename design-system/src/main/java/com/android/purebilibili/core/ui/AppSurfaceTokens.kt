package com.android.purebilibili.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 两值主题的表面 tokens。用这些访问器替代 feature 页面对
 * `MaterialTheme.colorScheme.surface` / `.background` 的直接读取，
 * 让 AMOLED、动态取色与 Miuix 桥接在两种主题风格下保持一致。
 *
 * - 卡片容器 = surfaceContainer（Miuix 桥接映射到同名上游语义角色）。
 * - 页面 / 分组列表 / chrome 背景 = 桥接后的 Material background。
 *   Miuix 的原生 background 是另一种表面，不能作为页面画布使用。
 */
object AppSurfaceTokens {

    /** Standard outline width for bordered cards, fields, and dialogs. */
    val OutlineWidth = 1.dp

    /** Standard frosted glass surface scrim opacity to prevent scrolling content bleed-through. */
    const val FrostedScrimAlpha = 0.65f

    fun resolveCardContainer(colorScheme: ColorScheme): Color = colorScheme.surfaceContainer

    fun resolveBackground(colorScheme: ColorScheme): Color = colorScheme.background

    fun resolveGroupedListContainer(colorScheme: ColorScheme): Color = resolveBackground(colorScheme)

    fun resolveChromeBackground(colorScheme: ColorScheme): Color = resolveBackground(colorScheme)

    fun resolveDivider(colorScheme: ColorScheme): Color = colorScheme.outlineVariant

    fun resolveSearchContainer(colorScheme: ColorScheme): Color = colorScheme.surfaceContainerHigh

    fun resolveSearchContent(colorScheme: ColorScheme): Color = colorScheme.onSurfaceVariant

    @Composable
    @ReadOnlyComposable
    fun cardContainer(): Color = resolveCardContainer(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun groupedListContainer(): Color = resolveGroupedListContainer(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun chromeBackground(): Color = resolveChromeBackground(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun divider(): Color = resolveDivider(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun searchContainer(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.surfaceContainerHigh,
        materialFallback = resolveSearchContainer(MaterialTheme.colorScheme)
    )

    @Composable
    @ReadOnlyComposable
    fun searchContent(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        materialFallback = resolveSearchContent(MaterialTheme.colorScheme)
    )

    @Composable
    @ReadOnlyComposable
    fun onSurfaceVariantSummary(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        materialFallback = MaterialTheme.colorScheme.onSurfaceVariant
    )

    @Composable
    @ReadOnlyComposable
    fun onSurfaceVariantActions(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
        materialFallback = MaterialTheme.colorScheme.onSurfaceVariant
    )

    @Composable
    @ReadOnlyComposable
    fun secondaryContainer(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.secondaryContainer,
        materialFallback = MaterialTheme.colorScheme.secondaryContainer
    )

    @Composable
    @ReadOnlyComposable
    fun onSecondaryContainer(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSecondaryContainer,
        materialFallback = MaterialTheme.colorScheme.onSecondaryContainer
    )

    @Composable
    @ReadOnlyComposable
    // 页面基础色不随玻璃开关分流：MD3 保留生成色板，Miuix 使用桥接后的原生 surface。
    // 卡片、搜索框与原生组件仍分别读取各自的容器角色。
    fun background(): Color = resolveBackground(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun surface(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.surface,
        materialFallback = MaterialTheme.colorScheme.surface
    )

    @Composable
    @ReadOnlyComposable
    fun surfaceContainer(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.surfaceContainer,
        materialFallback = MaterialTheme.colorScheme.surfaceContainer
    )

    @Composable
    @ReadOnlyComposable
    fun surfaceContainerHigh(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.surfaceContainerHigh,
        materialFallback = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    @Composable
    @ReadOnlyComposable
    fun surfaceContainerHighest(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.surfaceContainerHighest,
        materialFallback = MaterialTheme.colorScheme.surfaceContainerHighest
    )

    @Composable
    @ReadOnlyComposable
    fun onSurface(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurface,
        materialFallback = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    @ReadOnlyComposable
    fun onSurfaceSecondary(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurfaceSecondary,
        materialFallback = MaterialTheme.colorScheme.onSurfaceVariant
    )

    @Composable
    @ReadOnlyComposable
    fun onSurfaceContainer(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurfaceContainer,
        materialFallback = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    @ReadOnlyComposable
    fun onSurfaceContainerHigh(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        materialFallback = MaterialTheme.colorScheme.onSurfaceVariant
    )

    @Composable
    @ReadOnlyComposable
    fun onSurfaceContainerHighest(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
        materialFallback = MaterialTheme.colorScheme.onSurfaceVariant
    )

    @Composable
    @ReadOnlyComposable
    fun primary(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.primary,
        materialFallback = MaterialTheme.colorScheme.primary
    )

    /** Shared subtle selection fill for opaque Miuix navigation and categories. */
    @Composable
    @ReadOnlyComposable
    fun selectionContainer(): Color = primary().copy(alpha = 0.14f)

    @Composable
    @ReadOnlyComposable
    fun primaryContainer(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.primaryContainer,
        materialFallback = MaterialTheme.colorScheme.primaryContainer,
    )

    @Composable
    @ReadOnlyComposable
    fun onPrimaryContainer(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.onPrimaryContainer,
        materialFallback = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    @Composable
    @ReadOnlyComposable
    fun outline(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.outline,
        materialFallback = MaterialTheme.colorScheme.outline
    )

    @Composable
    @ReadOnlyComposable
    fun error(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.error,
        materialFallback = MaterialTheme.colorScheme.error
    )

    @Composable
    @ReadOnlyComposable
    fun disabledContent(): Color = resolveMiuixSemanticColorComposable(
        miuixColor = MiuixTheme.colorScheme.disabledOnSurface,
        materialFallback = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    )

    fun resolveMiuixSemanticColor(
        isMiuix: Boolean,
        miuixColor: Color,
        materialFallback: Color
    ): Color = if (isMiuix) miuixColor else materialFallback

    @Composable
    @ReadOnlyComposable
    private fun resolveMiuixSemanticColorComposable(
        miuixColor: Color,
        materialFallback: Color
    ): Color {
        val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
        return resolveMiuixSemanticColor(
            isMiuix = isMiuix,
            miuixColor = miuixColor,
            materialFallback = materialFallback
        )
    }
}
