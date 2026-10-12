package com.android.purebilibili.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class StyleDetectorTest extends LintDetectorTest {
    @Override
    protected Detector getDetector() {
        return new StyleDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return StyleDetector.ISSUES;
    }

    // Source fixtures model Compose's resolved owners, overloads and property getters.
    private static final TestFile COLOR = kotlin("""
            package androidx.compose.ui.graphics
            @JvmInline value class Color(val packed: ULong) {
                companion object {
                    val Black = Color(0uL)
                    val White = Color(1uL)
                    val Red = Color(2uL)
                    val Blue = Color(3uL)
                    val Green = Color(4uL)
                    val Yellow = Color(5uL)
                    val Gray = Color(6uL)
                    val LightGray = Color(7uL)
                    val DarkGray = Color(8uL)
                    val Transparent = Color(9uL)
                }
            }
            fun Color(color: Int): Color = Color(color.toULong())
            fun Color(color: Long): Color = Color(color.toULong())
            fun Color(red: Float, green: Float, blue: Float, alpha: Float = 1f): Color = Color(0uL)
            fun Color(red: Int, green: Int, blue: Int, alpha: Int = 255): Color = Color(0uL)
            """).indented().to("src/main/java/androidx/compose/ui/graphics/Color.kt");
    private static final TestFile DP = kotlin("""
            package androidx.compose.ui.unit
            @JvmInline value class Dp(val value: Float) : Comparable<Dp> {
                override fun compareTo(other: Dp): Int = value.compareTo(other.value)
            }
            val Int.dp: Dp get() = Dp(toFloat())
            val Float.dp: Dp get() = Dp(this)
            val Double.dp: Dp get() = Dp(toFloat())
            """).indented().to("src/main/java/androidx/compose/ui/unit/Dp.kt");
    private static final TestFile SP = kotlin("""
            package androidx.compose.ui.unit
            @JvmInline value class TextUnit(val value: Float)
            val Int.sp: TextUnit get() = TextUnit(toFloat())
            val Float.sp: TextUnit get() = TextUnit(this)
            val Double.sp: TextUnit get() = TextUnit(toFloat())
            """).indented().to("src/main/java/androidx/compose/ui/unit/TextUnit.kt");
    private static final TestFile SHAPE = kotlin("""
            package androidx.compose.foundation.shape
            import androidx.compose.ui.unit.Dp
            class RoundedCornerShape(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp)
            fun RoundedCornerShape(size: Dp): RoundedCornerShape = RoundedCornerShape(size, size, size, size)
            """).indented().to("src/main/java/androidx/compose/foundation/shape/RoundedCornerShape.kt");
    private static final TestFile MOTION = kotlin("""
            package androidx.compose.animation.core
            class TweenSpec<T>
            class SpringSpec<T>
            fun <T> tween(durationMillis: Int = 300, delayMillis: Int = 0): TweenSpec<T> = TweenSpec()
            fun <T> spring(dampingRatio: Float = 1f, stiffness: Float = 1500f,
                           visibilityThreshold: T? = null): SpringSpec<T> = SpringSpec()
            """).indented().to("src/main/java/androidx/compose/animation/core/AnimationSpec.kt");
    private static final TestFile THEME = kotlin("""
            package androidx.compose.material3
            import androidx.compose.ui.graphics.Color
            class ColorScheme(val surface: Color, val background: Color, val primary: Color)
            object MaterialTheme {
                val colorScheme = ColorScheme(Color.Transparent, Color.Transparent, Color.Transparent)
            }
            """).indented().to("src/main/java/androidx/compose/material3/MaterialTheme.kt");
    private static final TestFile ANDROID = gradle("""
            plugins { id 'com.android.library' }
            android {
                namespace 'test.style'
                compileSdk 37
            }
            """).indented();
    private static final TestFile[] COMPOSE = {
            COLOR, DP, SP, SHAPE, MOTION, THEME, ANDROID, manifest().minSdk(26)
    };

    private static TestFile[] withCompose(TestFile... sources) {
        TestFile[] files = Arrays.copyOf(COMPOSE, COMPOSE.length + sources.length);
        System.arraycopy(sources, 0, files, COMPOSE.length, sources.length);
        // Kotlin fixtures already declare complete project-relative source-set paths.
        // Clear the default "src" root so lint.xml and the Android model see those exact paths.
        for (TestFile file : files) file.within(null);
        return files;
    }

    @Test
    public void testResolvedColorLiteralsNamedColorsAndAliases() {
        lint().allowMissingSdk().issues(StyleDetector.COLOR).files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home.components
                import androidx.compose.ui.graphics.Color as Ink
                import androidx.compose.ui.graphics.Color.Companion.Red as ErrorInk
                fun draw(dynamic: Int, component: Float) {
                    Ink(0xFFFF0000)
                    Ink(16_711_680)
                    Ink(0b1111_0000)
                    Ink(255, 0, 0)
                    Ink(1f, 0f, 0f)
                    Ink(red = .5f, green = 0f, blue = 0f)
                    Ink(red = component, green = 0f, blue = 0f)
                    Ink.Black
                    ErrorInk
                    Ink(dynamic)
                    Ink.Transparent
                }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/components/Colors.kt")))
                .run().expectErrorCount(9).expectWarningCount(0);
    }

    @Test
    public void testDpAndSpAreLiteralUnitsNotEveryDynamicExpression() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION, StyleDetector.TYPOGRAPHY)
                .files(withCompose(kotlin("""
                package com.android.purebilibili.feature.dynamic
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.unit.sp
                fun draw(size: Int) {
                    8.dp
                    0.dp
                    2.5.dp
                    (-4).dp
                    12.sp
                    1.5f.sp
                    size.dp
                    size.sp
                    (size + 4).dp
                    (2 + 4).sp
                }
                """).indented().to("src/main/java/com/android/purebilibili/feature/dynamic/Dimensions.kt")))
                .run().expectErrorCount(6).expectWarningCount(0)
                .expectContains("Dimensions.kt:5: Error:")
                .expectContains("Dimensions.kt:9: Error:")
                .expectContains("[BiliPaiHardcodedDimension]")
                .expectContains("[BiliPaiHardcodedTypography]");
    }

    @Test
    public void testResolvedShapeMotionAndDirectSurfaceReads() {
        lint().allowMissingSdk().issues(StyleDetector.SHAPE, StyleDetector.MOTION, StyleDetector.SURFACE)
                .files(withCompose(kotlin("""
                package com.android.purebilibili.feature.following
                import androidx.compose.foundation.shape.RoundedCornerShape as Corner
                import androidx.compose.animation.core.tween as animate
                import androidx.compose.animation.core.spring
                import androidx.compose.material3.MaterialTheme as Theme
                import androidx.compose.ui.unit.Dp
                import androidx.compose.ui.unit.dp
                fun draw(radius: Dp, duration: Int, damping: Float) {
                    Corner(4.dp)
                    Corner(topStart = radius, topEnd = 5.dp, bottomEnd = radius, bottomStart = radius)
                    Corner(radius)
                    animate<Float>(durationMillis = 250)
                    animate<Float>(durationMillis = duration, delayMillis = 10)
                    animate<Float>(durationMillis = duration)
                    spring<Float>(dampingRatio = .8f, stiffness = 100f)
                    spring<Float>(dampingRatio = damping)
                    Theme.colorScheme.surface
                    Theme.colorScheme.background
                    Theme.colorScheme.primary
                    val runtimeScheme = Theme.colorScheme
                    runtimeScheme.surface
                }
                """).indented().to("src/main/java/com/android/purebilibili/feature/following/Visuals.kt")))
                .run().expectErrorCount(7).expectWarningCount(0)
                .expectContains("Visuals.kt:9: Error:")
                .expectContains("Visuals.kt:12: Error:")
                .expectContains("Visuals.kt:17: Error:")
                .expectContains("[BiliPaiHardcodedShape]")
                .expectContains("[BiliPaiHardcodedMotion]")
                .expectContains("[BiliPaiHardcodedSurface]");
    }

    @Test
    public void testNamedSurfaceTokensDoNotExemptUiReadsOrOtherSchemes() {
        lint().allowMissingSdk().issues(StyleDetector.SURFACE)
                .files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.material3.ColorScheme
                import androidx.compose.material3.MaterialTheme
                object SurfacePolicy {
                    val Base = MaterialTheme.colorScheme.surface
                    val Background = MaterialTheme.colorScheme.background
                    fun draw(scheme: ColorScheme) {
                        MaterialTheme.colorScheme.surface
                        MaterialTheme.colorScheme.background
                        scheme.surface
                        scheme.background
                    }
                }
                object OtherTheme { val colorScheme = MaterialTheme.colorScheme }
                fun drawOtherTheme() {
                    OtherTheme.colorScheme.surface
                    OtherTheme.colorScheme.background
                    MaterialTheme.colorScheme.primary
                }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/SurfacePolicy.kt")))
                .run().expectErrorCount(2).expectWarningCount(0);
    }

    @Test
    public void testCommentsStringsDeclarationsAndSameNamedFakeApisAreClean() {
        lint().allowMissingSdk().files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                /** Color.Red, Color(0xFFFF0000), 8.dp, 12.sp and tween(250) are examples only. */
                class Color(val value: Int) { companion object { val Red = Color(1) } }
                val Int.dp: Int get() = this
                val Int.sp: Int get() = this
                fun RoundedCornerShape(size: Int) = size
                fun tween(durationMillis: Int) = durationMillis
                fun spring(stiffness: Int) = stiffness
                class Scheme(val surface: Int, val background: Int)
                object MaterialTheme { val colorScheme = Scheme(1, 2) }
                fun fakeUi() {
                    // Color(255), Color.Black and 12.dp are not calls.
                    val prose = "Color(255), 8.dp, 12.sp, MaterialTheme.colorScheme.surface"
                    Color(255)
                    Color.Red
                    8.dp
                    12.sp
                    RoundedCornerShape(4.dp)
                    tween(250)
                    spring(100)
                    MaterialTheme.colorScheme.surface
                    MaterialTheme.colorScheme.background
                }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/FakeApis.kt")))
                .run().expectClean();
    }

    @Test
    public void testNamedObjectPropertyTokensDoNotExemptUiInSameObjectOrFile() {
        lint().allowMissingSdk().issues(StyleDetector.COLOR, StyleDetector.DIMENSION, StyleDetector.TYPOGRAPHY)
                .files(withCompose(kotlin("""
                package com.android.purebilibili.feature.live
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.unit.sp
                object BrandPalette {
                    val Danger = Color.Red
                    val Padding = 8.dp
                    val Label = 12.sp
                    fun draw() { Color.White; 5.dp; 13.sp }
                }
                fun unrelatedUi() { Color.Black; 9.dp; 14.sp }
                """).indented().to("src/main/java/com/android/purebilibili/feature/live/BrandPalette.kt")))
                .run().expectErrorCount(6).expectWarningCount(0);
    }

    @Test
    public void testTopLevelSpecPropertyTokensAndAllSixRules() {
        lint().allowMissingSdk().files(withCompose(kotlin("""
                package com.android.purebilibili.feature.list
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.unit.sp
                import androidx.compose.foundation.shape.RoundedCornerShape
                import androidx.compose.animation.core.tween
                import androidx.compose.material3.MaterialTheme
                val Accent = Color.Red
                val Gap = 8.dp
                val Label = 12.sp
                val Shape = RoundedCornerShape(4.dp)
                val Motion = tween<Float>(250)
                val Surface = MaterialTheme.colorScheme.surface
                """).indented().to("src/main/java/com/android/purebilibili/feature/list/VisualSpec.kt")))
                .run().expectClean();
    }

    @Test
    public void testNamedMotionFactoriesDoNotExemptUiFunctions() {
        lint().allowMissingSdk().issues(StyleDetector.MOTION)
                .files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.animation.core.tween
                import androidx.compose.animation.core.spring
                fun <T> chromeEnterMotionSpec() = tween<T>(durationMillis = 180)
                fun <T> chromeReleaseMotionSpec() = (spring<T>(dampingRatio = 0.62f, stiffness = 560f))
                fun <T> chromeExitMotionSpec(): androidx.compose.animation.core.TweenSpec<T> {
                    return tween<T>(durationMillis = 240)
                }
                fun draw() = tween<Float>(durationMillis = 250)
                fun uiMotionSpec() { spring<Float>(stiffness = 100f) }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/ChromeMotionSpec.kt")))
                .run().expectErrorCount(2).expectWarningCount(0);
    }

    @Test
    public void testMutableLocalLambdaAndGetterAreNotTokenDefinitions() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION)
                .files(withCompose(kotlin("""
                package com.android.purebilibili.feature.watchlater
                import androidx.compose.ui.unit.dp
                object LayoutPolicy {
                    var mutableSize = 8.dp
                    val ui = { 9.dp }
                    val computedSize get() = 10.dp
                    fun draw() { val local = 11.dp }
                }
                object Ordinary { val size = 12.dp }
                """).indented().to("src/main/java/com/android/purebilibili/feature/watchlater/LayoutPolicy.kt")))
                .run().expectErrorCount(5).expectWarningCount(0);
    }

    @Test
    public void testSameBasenameTestDoesNotExemptProductionFile() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION)
                .files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.unit.dp
                fun draw() = 7.dp
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/TestedSpec.kt"),
                        kotlin("""
                package com.android.purebilibili.feature.home
                class TestedSpecTest
                """).indented().to("src/test/java/com/android/purebilibili/feature/home/TestedSpecTest.kt")))
                .run().expectErrorCount(1).expectWarningCount(0);
    }

    @Test
    public void testOnlySixMigratedPackagesAndProductionSourcesAreChecked() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION).files(withCompose(
                kotlin("""
                package com.android.purebilibili.feature.video
                import androidx.compose.ui.unit.dp
                fun draw() = 8.dp
                """).indented().to("src/main/java/com/android/purebilibili/feature/video/Ui.kt"),
                kotlin("""
                package com.android.purebilibili.feature.homebrew
                import androidx.compose.ui.unit.dp
                fun draw() = 8.dp
                """).indented().to("src/main/java/com/android/purebilibili/feature/homebrew/Ui.kt"),
                kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.unit.dp
                fun testUi() = 8.dp
                """).indented().to("src/test/java/com/android/purebilibili/feature/home/UiTest.kt")))
                .run().expectClean();
    }

    @Test
    public void testLintXmlPathExemptionIsRuleSpecific() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION, StyleDetector.TYPOGRAPHY)
                .files(withCompose(xml("lint.xml", """
                <lint>
                    <issue id="BiliPaiHardcodedDimension">
                        <ignore path="src/main/java/com/android/purebilibili/feature/home/Approved.kt" />
                    </issue>
                </lint>
                """).indented(), kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.unit.sp
                fun draw() { 8.dp; 12.sp }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/Approved.kt"),
                        kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.unit.dp
                fun drawAnother() = 9.dp
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/Other.kt")))
                .run().expectErrorCount(2).expectWarningCount(0)
                .expectContains("[BiliPaiHardcodedDimension]")
                .expectContains("[BiliPaiHardcodedTypography]");
    }

    @Test
    public void testRealApiImportsDoNotTurnCommentsOrStringsIntoCalls() {
        lint().allowMissingSdk().files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.unit.sp
                import androidx.compose.foundation.shape.RoundedCornerShape
                import androidx.compose.animation.core.tween
                import androidx.compose.animation.core.spring
                import androidx.compose.material3.MaterialTheme
                /** Color.Red, RoundedCornerShape(4.dp), tween(250), spring(stiffness = 100f). */
                fun examplesOnly() {
                    // 8.dp, 12.sp, Color(255), MaterialTheme.colorScheme.surface
                    val example = "Color.Black; 8.dp; 12.sp; MaterialTheme.colorScheme.background"
                }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/Examples.kt")))
                .run().expectClean();
    }

    @Test
    public void testStandardSuppression() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION).files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.unit.dp
                @Suppress("BiliPaiHardcodedDimension")
                fun approvedUi() = 8.dp
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/Suppressed.kt")))
                .run().expectClean();
    }

    @Test
    public void testValueProducingStyleFactoriesDoNotExemptUiInPolicyFiles() {
        lint().allowMissingSdk().files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.unit.Dp
                import androidx.compose.ui.unit.TextUnit
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.unit.sp
                import androidx.compose.animation.core.spring
                import androidx.compose.animation.core.tween
                data class PanelStyle(val width: Dp, val label: TextUnit, val color: Color)
                data class DefaultPalette(val color: Color)
                data class PanelMetrics(val height: Dp, val spacing: Dp)
                data class OverlayPadding(val horizontal: Dp, val vertical: Dp)
                fun resolvePanelStyle(compact: Boolean): PanelStyle {
                    return when {
                        compact -> PanelStyle(32.dp, 12.sp, Color(0xFF444444))
                        else -> PanelStyle(48.dp, 15.sp, Color(0xFFDDDDDD))
                    }
                }
                fun resolveReadingWidth(): Dp = 480.dp
                fun createDefaultPalette() = DefaultPalette(Color(0xFF6750A4))
                fun imageCloseSpring() = spring<Float>(dampingRatio = 1f, stiffness = 600f)
                fun resolvePanelMetrics() = PanelMetrics(0.dp, 0.dp)
                fun resolveOverlayPadding(
                    horizontal: Dp = 16.dp,
                    vertical: Dp = 16.dp,
                ): OverlayPadding = OverlayPadding(horizontal, vertical)
                fun draw() {
                    6.dp
                    14.sp
                    Color.Red
                    tween<Float>(180)
                }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/PanelStylePolicy.kt")))
                .run().expectErrorCount(4).expectWarningCount(0)
                .expectContains("[BiliPaiHardcodedColor]")
                .expectContains("[BiliPaiHardcodedTypography]")
                .expectContains("[BiliPaiHardcodedMotion]");
    }

    @Test
    public void testPolicyNamesDoNotExemptCallbacksOrNonReturnedUiArguments() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION).files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.unit.Dp
                import androidx.compose.ui.unit.dp
                data class PanelStyle(val width: Dp, val onClick: () -> Unit = {})
                fun paint(width: Dp) {}
                fun resolvePanelStyle(): PanelStyle {
                    paint(22.dp)
                    return PanelStyle(32.dp, onClick = { paint(9.dp) })
                }
                fun draw() { val local = 11.dp }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/PanelStylePolicy.kt")))
                .run().expectErrorCount(3).expectWarningCount(0);
    }

    @Test
    public void testZeroUnitsAreAllowedOnlyForResolvedIdentityRoles() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION, StyleDetector.TYPOGRAPHY)
                .files(withCompose(
                        kotlin("""
                                package androidx.compose.foundation.layout
                                import androidx.compose.ui.unit.Dp
                                fun padding(all: Dp) {}
                                fun offset(x: Dp, y: Dp) {}
                                fun width(width: Dp) {}
                                """).indented().to("src/main/java/androidx/compose/foundation/layout/Size.kt"),
                        kotlin("""
                                package androidx.compose.material3
                                import androidx.compose.ui.unit.Dp
                                import androidx.compose.ui.unit.TextUnit
                                object ButtonDefaults {
                                    fun buttonElevation(defaultElevation: Dp) {}
                                }
                                fun Text(fontSize: TextUnit, letterSpacing: TextUnit) {}
                                """).indented().to("src/main/java/androidx/compose/material3/Text.kt"),
                        kotlin("""
                                package com.android.purebilibili.core.ui.components
                                import androidx.compose.ui.unit.Dp
                                fun AppSurface(tonalElevation: Dp) {}
                                """).indented().to("src/main/java/com/android/purebilibili/core/ui/components/AppSurface.kt"),
                        kotlin("""
                                package com.android.purebilibili.feature.home
                                import androidx.compose.foundation.layout.padding
                                import androidx.compose.foundation.layout.offset
                                import androidx.compose.foundation.layout.width
                                import androidx.compose.material3.ButtonDefaults
                                import androidx.compose.material3.Text
                                import androidx.compose.ui.unit.Dp
                                import androidx.compose.ui.unit.TextUnit
                                import androidx.compose.ui.unit.dp
                                import androidx.compose.ui.unit.sp
                                object Pretender { fun padding(all: Dp) {} }
                                fun draw(size: Dp, font: TextUnit) {
                                    ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    com.android.purebilibili.core.ui.components.AppSurface(tonalElevation = 0.dp)
                                    padding(0.dp)
                                    offset(0.dp, 0.dp)
                                    size.coerceAtLeast(0.dp)
                                    size.coerceIn(minimumValue = 0.dp, maximumValue = size)
                                    Text(fontSize = font, letterSpacing = 0.sp)
                                    width(0.dp)
                                    size.coerceAtMost(0.dp)
                                    size.coerceIn(minimumValue = size, maximumValue = 0.dp)
                                    Text(fontSize = 0.sp, letterSpacing = font)
                                    Pretender.padding(0.dp)
                                }
                                """).indented().to("src/main/java/com/android/purebilibili/feature/home/ZeroUnits.kt")))
                .run().expectErrorCount(5).expectWarningCount(0);
    }

    @Test
    public void testRoundedCornersAreNotReportedAgainAsDimensions() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION, StyleDetector.SHAPE)
                .files(withCompose(kotlin("""
                        package com.android.purebilibili.feature.home
                        import androidx.compose.foundation.shape.RoundedCornerShape
                        import androidx.compose.ui.unit.dp
                        fun draw() {
                            RoundedCornerShape(8.dp)
                            RoundedCornerShape(0.dp)
                        }
                        """).indented().to("src/main/java/com/android/purebilibili/feature/home/Corners.kt")))
                .run().expectErrorCount(1).expectWarningCount(0)
                .expectContains("[BiliPaiHardcodedShape]");
    }

    @Test
    public void testContrastBackgroundInputDoesNotExemptDisplayedSurfaceReads() {
        lint().allowMissingSdk().issues(StyleDetector.SURFACE).files(withCompose(
                kotlin("""
                        package com.android.purebilibili.core.theme
                        import androidx.compose.ui.graphics.Color
                        fun resolveAccessibleContainerColors(
                            containerColor: Color, contentColor: Color, backgroundColor: Color
                        ): Color = containerColor
                        """).indented().to("src/main/java/com/android/purebilibili/core/theme/ThemeContrastPolicy.kt"),
                kotlin("""
                        package com.android.purebilibili.feature.dynamic
                        import androidx.compose.material3.MaterialTheme
                        import androidx.compose.ui.graphics.Color
                        import com.android.purebilibili.core.theme.resolveAccessibleContainerColors
                        fun paint(backgroundColor: Color) {}
                        fun draw() {
                            resolveAccessibleContainerColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                backgroundColor = MaterialTheme.colorScheme.surface
                            )
                            paint(backgroundColor = MaterialTheme.colorScheme.surface)
                        }
                        """).indented().to("src/main/java/com/android/purebilibili/feature/dynamic/Contrast.kt")))
                .run().expectErrorCount(2).expectWarningCount(0);
    }

    @Test
    public void testGenericAndAnonymousFactoryTypesKeepCheckingUiUseSites() {
        lint().allowMissingSdk().issues(StyleDetector.DIMENSION).files(withCompose(kotlin("""
                package com.android.purebilibili.feature.home
                import androidx.compose.ui.unit.dp
                fun <T> resolveItemOrNull(items: List<T>, page: Int): T? = items.getOrNull(page)
                fun resolveAnonymousPolicy() = object { val width get() = 8.dp }
                fun draw() { 7.dp }
                """).indented().to("src/main/java/com/android/purebilibili/feature/home/GenericPolicy.kt")))
                .run().expectErrorCount(2).expectWarningCount(0);
    }
}
