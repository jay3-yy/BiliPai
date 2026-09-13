package com.android.purebilibili.core.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPrimitiveComponentsStructureTest {

    @Test
    fun singleChoiceRowsDelegateSelectionAndColorsToTheSharedThemeLayer() {
        val source = loadSource("components/AppSingleChoiceRow.kt")

        assertTrue(source.contains("fun AppSingleChoiceRow("))
        assertTrue(source.contains("AppRadioButton("))
        assertTrue(source.contains("AppSurfaceTokens.surfaceContainerHigh()"))
        assertTrue(source.contains("AppSurfaceTokens.onSurfaceContainerHigh()"))
        assertFalse(source.contains("primaryContainer"))
        assertFalse(source.contains("if (selected)"))
    }

    @Test
    fun remainingLegacyPrimitiveApisStayAvailableDuringRendererMigration() {
        val source = loadSource()

        assertTrue(source.contains("fun AppButton("))
        assertTrue(source.contains("colors: ButtonColors"))
        assertTrue(source.contains("fun AppTextButton("))
        assertTrue(source.contains("AppUiStyle.MIUIX -> AppMiuixButton("))
        assertTrue(source.contains("AppUiStyle.MATERIAL3 -> Button("))
        assertTrue(source.contains("AppUiStyle.MATERIAL3 -> TextButton("))
        assertTrue(source.contains("fun AppOutlinedTextField("))
        assertTrue(source.contains("shouldUseMiuixOutlinedTextField("))
        assertTrue(source.contains("MiuixTextField("))
        assertTrue(source.contains("OutlinedTextField("))
        assertTrue(source.contains("fun AppDropdownMenu("))
        assertTrue(source.contains("fun AppModalNavigationDrawer("))
        assertTrue(source.contains("fun AppNavigationDrawerItem("))
        assertTrue(source.contains("NavigationDrawerItem("))
        assertTrue(source.contains("fun AppOutlinedButton("))
        assertTrue(source.contains("OutlinedButton("))
        assertTrue(source.contains("fun AppAssistChip("))
        assertTrue(source.contains("AssistChip("))
        assertTrue(source.contains("fun AppFilterChip("))
        assertTrue(source.contains("FilterChip("))
        assertTrue(source.contains("fun AppFloatingActionButton("))
        assertTrue(source.contains("FloatingActionButton("))
        assertTrue(source.contains("fun AppSmallFloatingActionButton("))
        assertTrue(source.contains("SmallFloatingActionButton("))
        assertTrue(source.contains("LocalAppUiStyle.current == AppUiStyle.MIUIX"))
        assertTrue(source.contains("AppMiuixButton("))
        assertTrue(source.contains("AppMiuixChip("))
        assertTrue(source.contains("AppMiuixFloatingActionButton("))
        assertTrue(source.contains("AppMiuixSnackbar("))
        assertTrue(source.contains("AppMiuixNavigationDrawerItem("))
        assertTrue(source.contains("MiuixButtonDefaults.buttonColors()"))
        assertTrue(source.contains("MiuixButtonDefaults.InsideMargin"))
        assertTrue(source.contains("fun AppTab("))
        assertTrue(source.contains(") = Tab("))
        assertTrue(source.contains("fun AppPrimaryTabRow("))
        assertTrue(source.contains("TabRow("))
        assertTrue(source.contains("fun AppPrimaryScrollableTabRow("))
        assertTrue(source.contains("ScrollableTabRow("))
        assertTrue(source.contains("PiliPlusIndicatorDecelerate"))
        assertTrue(source.contains("PiliPlusIndicatorAccelerate"))
        assertTrue(source.contains("indicatorPositionProvider: (() -> Float)? = null"))
        assertEquals(2, source.windowed("divider = {}".length).count { it == "divider = {}" })
        assertFalse(source.contains("HorizontalDivider"))
        assertTrue(source.contains("resolveElasticTabIndicatorBounds("))
        assertTrue(source.contains(".wrapContentSize(Alignment.BottomStart)"))
        assertFalse(source.contains("matchContentSize = false"))
        assertTrue(source.contains("fun AppSuggestionChip("))
        assertTrue(source.contains("SuggestionChip("))
    }

    @Test
    fun nonGlassActionPrimitivesStayOnOfficialMiuixComponents() {
        val renderer = loadSource("renderer/miuix/AppMiuixActionPrimitives.kt")

        assertTrue(renderer.contains("MiuixButtonDefaults.MinHeight"))
        assertTrue(renderer.contains("MiuixButtonDefaults.MinWidth"))
        assertTrue(renderer.contains("MiuixButtonDefaults.CornerRadius"))
        assertTrue(renderer.contains("resolveMiuixNonGlassChipMetrics()"))
        assertTrue(renderer.contains("MiuixFloatingActionButton("))
        assertTrue(renderer.contains("MiuixSnackbarDefaults"))
        assertTrue(renderer.contains("BasicComponent("))
        assertTrue(renderer.contains("holdDownState = selected"))
        assertFalse(renderer.contains("import androidx.compose.material3"))
    }

    @Test
    fun surfaceFacadeRoutesEachThemeToItsNativeRenderer() {
        val facade = loadSource("components/AppSurface.kt")
        val material = loadSource("renderer/material3/AppMaterial3Surface.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixSurface.kt")

        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Surface("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixSurface("))
        assertTrue(facade.contains("Color.Unspecified"))
        assertTrue(facade.contains("Dp.Unspecified"))
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))

        assertTrue(material.contains("import androidx.compose.material3.Surface"))
        assertTrue(material.contains("import androidx.compose.material3.HorizontalDivider"))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.Surface"))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.HorizontalDivider"))
        assertFalse(miuix.contains("import androidx.compose.material3"))
    }

    @Test
    fun cardFacadeUsesControlledShapesAndNativeRenderers() {
        val primitiveSource = loadSource()
        val facade = loadSource("components/AppCard.kt")
        val material = loadSource("renderer/material3/AppMaterial3Card.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixCard.kt")

        assertFalse(primitiveSource.contains("fun AppCard("))
        assertTrue(facade.contains("sealed interface AppCardShape"))
        assertTrue(facade.contains("data class Semantic(val level: ContainerLevel)"))
        assertTrue(facade.contains("data class Uniform(val cornerRadius: Dp)"))
        assertTrue(facade.contains("data class AppCardColors("))
        assertTrue(facade.contains("enum class AppCardVariant"))
        assertTrue(facade.contains("content: @Composable ColumnScope.() -> Unit"))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Card("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixCard("))
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))
        assertFalse(facade.contains("import androidx.compose.ui.graphics.Shape"))

        assertTrue(material.contains("import androidx.compose.material3.Card"))
        assertTrue(material.contains("import androidx.compose.material3.ElevatedCard"))
        assertTrue(material.contains("uiStyle = AppUiStyle.MATERIAL3"))
        assertTrue(material.contains("is AppCardShape.Uniform -> RoundedCornerShape(cornerRadius)"))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.Card"))
        assertTrue(miuix.contains("CardDefaults.CornerRadius"))
        assertTrue(miuix.contains("uiStyle = AppUiStyle.MIUIX"))
        assertFalse(miuix.contains("import androidx.compose.material3"))
        assertFalse(miuix.contains("import androidx.compose.ui.graphics.Shape"))
        assertFalse(facade.contains("48.dp"))
        assertFalse(material.contains("48.dp"))
        assertFalse(miuix.contains("48.dp"))
    }

    @Test
    fun iconButtonFacadeRoutesToNativeGeometryAndInteractionBoundaries() {
        val primitiveSource = loadSource()
        val facade = loadSource("components/AppIconButton.kt")
        val desktopInteraction = loadSource("components/AppDesktopInteraction.kt")
        val material = loadSource("renderer/material3/AppMaterial3IconButton.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixIconButton.kt")

        assertFalse(primitiveSource.contains("fun AppIconButton("))
        assertFalse(primitiveSource.contains("fun AppFilledIconButton("))
        assertTrue(facade.contains("data class AppIconButtonColors("))
        assertTrue(facade.contains("object AppIconButtonDefaults"))
        assertTrue(facade.contains("internal enum class AppIconButtonVariant"))
        assertTrue(facade.contains("fun AppIconButton("))
        assertTrue(facade.contains("fun AppFilledIconButton("))
        assertTrue(facade.contains("content: @Composable () -> Unit"))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3IconButton("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixIconButton("))
        assertEquals(8, facade.lineSequence().count { it.contains("Color = Color.Unspecified") })
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))
        assertFalse(facade.contains("import androidx.compose.ui.graphics.Shape"))
        assertFalse(facade.contains("cornerRadius"))

        assertTrue(material.contains("import androidx.compose.material3.IconButton"))
        assertTrue(material.contains("import androidx.compose.material3.FilledIconButton"))
        assertTrue(material.contains("IconButtonDefaults.standardShape"))
        assertTrue(material.contains("IconButtonDefaults.filledShape"))
        assertTrue(material.contains("defaultColors.copy("))
        assertTrue(material.contains("shape = nativeShape"))
        assertTrue(material.contains("modifier.appDesktopInteractionVisuals("))
        assertFalse(material.contains("minimumInteractiveComponentSize"))
        assertFalse(material.contains("import top.yukonga.miuix"))

        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.IconButton"))
        assertTrue(miuix.contains("MiuixIconButton("))
        assertTrue(miuix.contains("MiuixButtonDefaults.buttonColorsPrimary()"))
        assertTrue(miuix.contains("MiuixLocalContentColor provides contentColor"))
        assertTrue(miuix.contains("minWidth = AppChromeSizeTokens.MinimumTouchTarget"))
        assertTrue(miuix.contains("minHeight = AppChromeSizeTokens.MinimumTouchTarget"))
        assertTrue(miuix.contains("MiuixNativeCompactCornerRadiusDp.dp"))
        assertTrue(miuix.contains("if (interactionSource != null)"))
        assertTrue(miuix.contains(".then(pointerMirror)"))
        assertTrue(miuix.contains("awaitEachGesture"))
        assertTrue(miuix.contains("requireUnconsumed = false"))
        assertTrue(miuix.contains("pass = PointerEventPass.Initial"))
        assertTrue(miuix.contains("waitForUpOrCancellation(PointerEventPass.Initial)"))
        assertTrue(miuix.contains("PressInteraction.Press"))
        assertTrue(miuix.contains("PressInteraction.Release"))
        assertTrue(miuix.contains("PressInteraction.Cancel"))
        assertTrue(miuix.contains("finally"))
        assertTrue(miuix.contains("interactionSource.tryEmit(press)"))
        assertTrue(miuix.contains("interactionSource.tryEmit("))
        assertFalse(miuix.contains("interactionSource.emit("))
        assertFalse(miuix.contains(".clickable("))
        assertFalse(miuix.contains(".consume("))
        assertTrue(miuix.contains("cornerRadius = AppChromeSizeTokens.MiuixNativeCompactCornerRadiusDp.dp"))
        assertFalse(miuix.contains("import androidx.compose.material3"))

        assertEquals(2, desktopInteraction.lineSequence().count { it == "    shape: Shape? = null," })
        assertTrue(desktopInteraction.contains("import androidx.compose.ui.graphics.drawOutline"))
        assertFalse(desktopInteraction.contains("import androidx.compose.ui.graphics.drawscope.drawOutline"))
        assertTrue(desktopInteraction.contains("shape?.createOutline(size, layoutDirection, this)"))
        assertFalse(facade.contains("48.dp"))
        assertFalse(material.contains("48.dp"))
        assertFalse(miuix.contains("48.dp"))
        assertFalse(desktopInteraction.contains("48.dp"))
    }

    @Test
    fun listItemFacadeRoutesEachThemeToItsNativeRenderer() {
        val primitiveSource = loadSource()
        val facade = loadSource("components/AppListItem.kt")
        val material = loadSource("renderer/material3/AppMaterial3ListItem.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixListItem.kt")

        assertFalse(primitiveSource.contains("fun AppListItem("))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3ListItem("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixListItem("))
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))

        assertTrue(material.contains("import androidx.compose.material3.ListItem"))
        assertFalse(miuix.contains("BasicComponent("))
        assertTrue(miuix.contains(".heightIn(min = 48.dp)"))
        assertTrue(miuix.contains("leadingContent()"))
        assertTrue(miuix.contains("trailingContent()"))
        assertFalse(miuix.contains("import androidx.compose.material3"))
    }

    @Test
    fun badgeFacadeRoutesEachThemeToItsNativeRenderer() {
        val primitiveSource = loadSource()
        val facade = loadSource("components/AppBadge.kt")
        val material = loadSource("renderer/material3/AppMaterial3Badge.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixBadge.kt")

        assertFalse(primitiveSource.contains("fun AppBadge("))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Badge("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixBadge("))
        assertTrue(facade.contains("containerColor: Color = Color.Unspecified"))
        assertTrue(facade.contains("contentColor: Color = Color.Unspecified"))
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))

        assertTrue(material.contains("import androidx.compose.material3.Badge"))
        assertTrue(material.contains("BadgeDefaults.containerColor"))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.Badge"))
        assertTrue(miuix.contains("BadgeDefaults.containerColor"))
        assertTrue(miuix.contains("BadgeDefaults.contentColor"))
        assertFalse(miuix.contains("import androidx.compose.material3"))
    }

    @Test
    fun sliderFacadeMapsNeutralColorsToEachNativeRenderer() {
        val primitiveSource = loadSource()
        val facade = loadSource("components/AppSlider.kt")
        val material = loadSource("renderer/material3/AppMaterial3Slider.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixSlider.kt")

        assertFalse(primitiveSource.contains("fun AppSlider("))
        assertTrue(facade.contains("data class AppSliderColors("))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Slider("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixSlider("))
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))

        assertTrue(material.contains("import androidx.compose.material3.Slider"))
        assertTrue(material.contains("SliderDefaults.colors("))
        assertTrue(material.contains("modifier.appDesktopInteractionVisuals("))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.Slider"))
        assertTrue(miuix.contains("SliderDefaults.sliderColors("))
        assertTrue(miuix.contains("modifier.appDesktopFocusableItemVisuals(enabled)"))
        assertFalse(miuix.contains("import androidx.compose.material3"))
    }

    @Test
    fun progressFacadePreservesProvidersAndRoutesToNativeRenderers() {
        val primitiveSource = loadSource()
        val facade = loadSource("components/AppProgressIndicator.kt")
        val material = loadSource("renderer/material3/AppMaterial3ProgressIndicator.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixProgressIndicator.kt")

        assertFalse(primitiveSource.contains("fun AppCircularProgressIndicator("))
        assertFalse(primitiveSource.contains("fun AppLinearProgressIndicator("))
        assertEquals(2, facade.lineSequence().count { it == "fun AppCircularProgressIndicator(" })
        assertEquals(2, facade.lineSequence().count { it == "fun AppLinearProgressIndicator(" })
        assertTrue(facade.contains("progress: () -> Float"))
        assertTrue(facade.contains("color: Color = Color.Unspecified"))
        assertTrue(facade.contains("strokeWidth: Dp = Dp.Unspecified"))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3CircularProgressIndicator("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixCircularProgressIndicator("))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3LinearProgressIndicator("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixLinearProgressIndicator("))
        assertFalse(facade.contains("ProgressIndicatorDefaults"))
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))

        assertTrue(material.contains("import androidx.compose.material3.CircularProgressIndicator"))
        assertTrue(material.contains("import androidx.compose.material3.LinearProgressIndicator"))
        assertTrue(material.contains("progress = progress,"))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.CircularProgressIndicator"))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.LinearProgressIndicator"))
        assertTrue(miuix.contains("progress = progress(),"))
        assertFalse(miuix.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("48.dp"))
        assertFalse(material.contains("48.dp"))
        assertFalse(miuix.contains("48.dp"))
    }

    @Test
    fun primaryButtonLoadingUsesTheThemeAwareProgressFacade() {
        val source = loadSource("components/AppPrimaryButton.kt")

        assertTrue(source.contains("AppCircularProgressIndicator("))
        assertFalse(source.contains("import androidx.compose.material3.CircularProgressIndicator"))
        assertFalse(Regex("(?<!App)CircularProgressIndicator\\(").containsMatchIn(source))
    }

    @Test
    fun switchFacadeRoutesEachThemeToNativeDefaults() {
        val primitiveSource = loadSource()
        val facade = loadSource("components/AppSwitch.kt")
        val material = loadSource("renderer/material3/AppMaterial3Switch.kt")
        val miuix = loadSource("renderer/miuix/AppMiuixSwitch.kt")

        assertFalse(primitiveSource.contains("fun AppSwitch("))
        assertTrue(facade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Switch("))
        assertTrue(facade.contains("AppUiStyle.MIUIX -> AppMiuixSwitch("))
        assertTrue(facade.contains("showThumbIcon: Boolean = true"))
        assertFalse(facade.contains("SwitchColors"))
        assertFalse(facade.contains("import androidx.compose.material3"))
        assertFalse(facade.contains("import top.yukonga.miuix"))

        assertTrue(material.contains("import androidx.compose.material3.Switch"))
        assertTrue(material.contains("SwitchDefaults.colors()"))
        assertTrue(material.contains("Icons.Filled.Check"))
        assertTrue(material.contains("Icons.Filled.Close"))
        assertTrue(miuix.contains("import top.yukonga.miuix.kmp.basic.Switch"))
        assertTrue(miuix.contains("ProvideAppMiuixHapticFeedback"))
        assertTrue(miuix.contains("modifier.appDesktopFocusableItemVisuals("))
        assertFalse(miuix.contains("import androidx.compose.material3"))
    }

    @Test
    fun selectionFacadesRouteEachThemeToNativeRenderers() {
        val primitiveSource = loadSource()
        val checkboxFacade = loadSource("components/AppCheckbox.kt")
        val radioFacade = loadSource("components/AppRadioButton.kt")
        val materialCheckbox = loadSource("renderer/material3/AppMaterial3Checkbox.kt")
        val materialRadio = loadSource("renderer/material3/AppMaterial3RadioButton.kt")
        val miuixCheckbox = loadSource("renderer/miuix/AppMiuixCheckbox.kt")
        val miuixRadio = loadSource("renderer/miuix/AppMiuixRadioButton.kt")
        val miuixHaptic = loadSource("renderer/miuix/AppMiuixHapticFeedback.kt")

        assertFalse(primitiveSource.contains("fun AppCheckbox("))
        assertFalse(primitiveSource.contains("fun AppRadioButton("))
        assertTrue(checkboxFacade.contains("data class AppCheckboxColors("))
        assertTrue(checkboxFacade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Checkbox("))
        assertTrue(checkboxFacade.contains("AppUiStyle.MIUIX -> AppMiuixCheckbox("))
        assertTrue(radioFacade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3RadioButton("))
        assertTrue(radioFacade.contains("AppUiStyle.MIUIX -> AppMiuixRadioButton("))
        assertFalse(checkboxFacade.contains("import androidx.compose.material3"))
        assertFalse(radioFacade.contains("import androidx.compose.material3"))

        assertTrue(materialCheckbox.contains("import androidx.compose.material3.Checkbox"))
        assertTrue(materialRadio.contains("import androidx.compose.material3.RadioButton"))
        assertTrue(miuixCheckbox.contains("import top.yukonga.miuix.kmp.basic.Checkbox"))
        assertTrue(miuixCheckbox.contains("ToggleableState.On"))
        assertTrue(miuixRadio.contains("import top.yukonga.miuix.kmp.basic.RadioButton"))
        assertTrue(miuixCheckbox.contains("ProvideAppMiuixHapticFeedback"))
        assertTrue(miuixRadio.contains("ProvideAppMiuixHapticFeedback"))
        assertTrue(miuixHaptic.contains("LocalAppThemeConfig.current.hapticFeedbackEnabled"))
        assertTrue(miuixHaptic.contains("NoOpHapticFeedback"))
        assertFalse(miuixCheckbox.contains("import androidx.compose.material3"))
        assertFalse(miuixRadio.contains("import androidx.compose.material3"))
        assertFalse(
            loadSource("components/AppSelectionPreferenceComponents.kt")
                .contains("modifier = Modifier.size(48.dp)"),
        )
    }

    @Test
    fun textAndIconFacadesRouteEachThemeToNativeRenderers() {
        val primitiveSource = loadSource()
        val textFacade = loadSource("components/AppText.kt")
        val iconFacade = loadSource("components/AppIcon.kt")
        val themeDefaults = loadSource("AppPrimitiveThemeDefaults.kt")
        val materialText = loadSource("renderer/material3/AppMaterial3Text.kt")
        val materialIcon = loadSource("renderer/material3/AppMaterial3Icon.kt")
        val miuixText = loadSource("renderer/miuix/AppMiuixText.kt")
        val miuixIcon = loadSource("renderer/miuix/AppMiuixIcon.kt")

        assertFalse(primitiveSource.contains("fun AppText("))
        assertFalse(primitiveSource.contains("fun AppIcon("))
        assertEquals(4, textFacade.lineSequence().count { it == "fun AppText(" })
        assertEquals(4, iconFacade.lineSequence().count { it == "fun AppIcon(" })
        assertTrue(textFacade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Text("))
        assertTrue(textFacade.contains("AppUiStyle.MIUIX -> AppMiuixText("))
        assertTrue(iconFacade.contains("AppUiStyle.MATERIAL3 -> AppMaterial3Icon("))
        assertTrue(iconFacade.contains("AppUiStyle.MIUIX -> AppMiuixIcon("))
        assertTrue(textFacade.contains("globalTextTapCopy"))
        assertFalse(textFacade.contains("import androidx.compose.material3"))
        assertFalse(textFacade.contains("import top.yukonga.miuix"))
        assertFalse(iconFacade.contains("import androidx.compose.material3"))
        assertFalse(iconFacade.contains("import top.yukonga.miuix"))

        assertTrue(themeDefaults.contains("MaterialLocalTextStyle.current"))
        assertTrue(themeDefaults.contains("MiuixTheme.textStyles.main"))
        assertFalse(themeDefaults.contains("LocalTextStyles"))
        assertFalse(themeDefaults.contains(".kmp.basic."))
        assertTrue(materialText.contains("import androidx.compose.material3.Text"))
        assertTrue(materialIcon.contains("import androidx.compose.material3.Icon"))
        assertTrue(miuixText.contains("import top.yukonga.miuix.kmp.basic.Text"))
        assertTrue(miuixIcon.contains("import top.yukonga.miuix.kmp.basic.Icon"))
        assertFalse(miuixText.contains("import androidx.compose.material3"))
        assertFalse(miuixIcon.contains("import androidx.compose.material3"))
    }

    private fun loadSource(): String {
        val path = "src/main/java/com/android/purebilibili/core/ui/components/AppPrimitiveComponents.kt"
        return listOf(
            File(path),
            File("design-system/$path"),
        ).firstOrNull(File::exists)?.readText()
            ?: error("Cannot locate AppPrimitiveComponents.kt from ${File(".").absolutePath}")
    }

    private fun loadSource(relativePath: String): String {
        val path = "src/main/java/com/android/purebilibili/core/ui/$relativePath"
        return listOf(
            File(path),
            File("design-system/$path"),
        ).firstOrNull(File::exists)?.readText()
            ?: error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
