package com.android.purebilibili

import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.InflaterInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class MainActivityAppCompatContractTest {

    @Test
    fun mainActivity_shouldExtendAppCompatActivity_forRuntimeLocaleUpdates() {
        assertTrue(
            AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java)
        )
    }

    @Test
    fun splashPostTheme_shouldUseAppCompatDayNightMainTheme() {
        val lightThemes = loadResourceText("values/themes.xml")
        val nightThemes = loadResourceText("values-night/themes.xml")

        assertTrue(
            lightThemes.contains("""<item name="postSplashScreenTheme">@style/Theme.PureBiliBili.Main</item>"""),
            "Light splash theme should hand off to Theme.PureBiliBili.Main"
        )
        assertTrue(
            nightThemes.contains("""<item name="postSplashScreenTheme">@style/Theme.PureBiliBili.Main</item>"""),
            "Night splash theme should hand off to Theme.PureBiliBili.Main"
        )
        assertTrue(
            lightThemes.contains("""<style name="Theme.PureBiliBili.Main" parent="Theme.AppCompat.DayNight.NoActionBar">"""),
            "Light main theme must use an AppCompat descendant for MainActivity"
        )
        assertTrue(
            nightThemes.contains("""<style name="Theme.PureBiliBili.Main" parent="Theme.AppCompat.DayNight.NoActionBar">"""),
            "Night main theme must use an AppCompat descendant for MainActivity"
        )
    }

    @Test
    fun maidSplashTheme_shouldUseRoundedLayeredDrawables() {
        val lightThemes = loadResourceText("values/themes.xml")
        val nightThemes = loadResourceText("values-night/themes.xml")

        assertTrue(
            lightThemes.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon_blue_snow_maid</item>"""),
            "Light splash theme should use the rounded layered maid drawable"
        )
        assertTrue(
            nightThemes.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon_blue_snow_maid</item>"""),
            "Night splash theme should use the rounded layered maid drawable"
        )
        assertTrue(
            lightThemes.contains("""<item name="windowSplashScreenIconBackgroundColor">@android:color/transparent</item>"""),
            "Light splash theme should not add a second icon background around the adaptive icon"
        )
        listOf("drawable", "drawable-night").forEach { directory ->
            listOf("splash_icon_blue_snow_maid.xml", "splash_icon_blue_snow_maid_front.xml")
                .forEach { fileName ->
                    val drawable = loadResourceText("$directory/$fileName")
                    assertTrue(drawable.contains("""<corners android:radius="26dp" />"""))
                    assertTrue(drawable.contains("""@mipmap/ic_launcher_blue_snow_maid"""))
                }
        }
        assertTrue(
            !splashDrawableVectorExists(),
            "Splash theme should not keep the hand-drawn drawable foreground vector"
        )
    }

    @Test
    fun bilipaiWhiteSplashTheme_shouldUseReadableLightBackground() {
        val lightThemes = loadResourceText("values/themes.xml")
        val bilipaiWhiteTheme = Regex(
            """<style name="Theme\.PureBiliBili\.Splash\.BiliPaiWhite"[\s\S]*?</style>"""
        ).find(lightThemes)?.value.orEmpty()

        assertTrue(
            bilipaiWhiteTheme.contains("""<item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_bilipai_white</item>"""),
            "BiliPai white splash theme should use the matching white icon"
        )
        assertTrue(
            bilipaiWhiteTheme.contains("""<item name="windowSplashScreenBackground">@color/splash_bilipai_white_background</item>"""),
            "BiliPai white splash theme should not inherit a white splash background because its rounded white shell becomes invisible"
        )
    }

    @Test
    fun bilipaiWhiteIcon_shouldUseCenteredFlatGeneratedArtwork() {
        val background = loadResourceText("drawable/ic_launcher_bilipai_white_background.xml")
        assertTrue(
            background.contains("android:fillColor=\"#FFFFFFFF\"") &&
                !background.contains("<gradient") &&
                !background.contains("#161C5C75"),
            "BiliPai white adaptive background should remain flat white without the retired glossy haze"
        )

        val rows = readPngRgbaRows(
            loadResourceFile("mipmap-xxxhdpi/ic_launcher_bilipai_white_foreground.png")
        )
        val imageWidth = rows.first().size / 4
        val opaquePoints = buildList {
            rows.forEachIndexed { y, row ->
                for (x in 0 until imageWidth) {
                    if (row[x * 4 + 3] >= 128) add(x to y)
                }
            }
        }
        val minX = opaquePoints.minOf { it.first }
        val maxX = opaquePoints.maxOf { it.first }
        val minY = opaquePoints.minOf { it.second }
        val maxY = opaquePoints.maxOf { it.second }
        val artworkWidthRatio = (maxX - minX + 1).toFloat() / imageWidth
        val artworkHeightRatio = (maxY - minY + 1).toFloat() / rows.size
        val artworkCenterX = (minX + maxX) / 2f
        val artworkCenterY = (minY + maxY) / 2f
        val canvasCenter = (imageWidth - 1) / 2f

        assertTrue(
            artworkWidthRatio in 0.64f..0.67f && artworkHeightRatio in 0.57f..0.60f,
            "BiliPai white TV and wordmark should stay large enough to read at launcher size"
        )
        assertTrue(
            artworkCenterX == canvasCenter && artworkCenterY == canvasCenter,
            "BiliPai white TV and wordmark should remain exactly centered in the adaptive foreground"
        )
    }

    @Test
    fun bilipaiMonet_shouldUseTheOfficialThemedAdaptiveIconContract() {
        listOf(
            "ic_launcher_bilipai",
            "ic_launcher_bilipai_round",
            "ic_launcher_bilipai_pink",
            "ic_launcher_bilipai_pink_round",
            "ic_launcher_bilipai_white",
            "ic_launcher_bilipai_white_round"
        ).forEach { iconName ->
            val adaptiveIcon = loadResourceText("mipmap-anydpi-v26/$iconName.xml")
            assertTrue(
                adaptiveIcon.contains(
                    "<monochrome android:drawable=\"@mipmap/ic_launcher_bilipai_monet_foreground\" />"
                ),
                "$iconName should derive its themed icon from the real BiliPai logo, not a hand-drawn substitute"
            )
        }
        assertTrue(
            !resourcePathExists("drawable/ic_launcher_bilipai_monochrome.xml"),
            "The retired hand-drawn TV/play monochrome icon should not remain packaged"
        )

        val splashAdaptiveIcon = loadResourceText("mipmap-anydpi-v26/splash_icon_bilipai_monet.xml")
        assertTrue(
            splashAdaptiveIcon.contains("@color/splash_bilipai_monet_background") &&
                splashAdaptiveIcon.contains("@drawable/splash_icon_bilipai_monet_foreground"),
            "BiliPai Monet splash should use a dedicated adaptive icon so the system starting window is dynamically colored"
        )
        assertTrue(
            loadResourceText("values-v31/colors.xml").contains(
                "<color name=\"splash_bilipai_monet_background\">@android:color/system_accent1_100</color>"
            ) && loadResourceText("values-v31/colors.xml").contains(
                "<color name=\"splash_bilipai_monet_foreground\">@android:color/system_accent1_700</color>"
            ),
            "Light Monet splash should follow AOSP themed-icon dynamic color roles"
        )
        assertTrue(
            loadResourceText("values-night-v31/colors.xml").contains(
                "<color name=\"splash_bilipai_monet_background\">@android:color/system_accent2_800</color>"
            ) && loadResourceText("values-night-v31/colors.xml").contains(
                "<color name=\"splash_bilipai_monet_foreground\">@android:color/system_accent1_200</color>"
            ),
            "Dark Monet splash should follow AOSP themed-icon dynamic color roles"
        )
        listOf("values/themes.xml", "values-night/themes.xml").forEach { themePath ->
            val monetTheme = Regex(
                """<style name="Theme\.PureBiliBili\.Splash\.BiliPaiMonet"[\s\S]*?</style>"""
            ).find(loadResourceText(themePath))?.value.orEmpty()
            assertTrue(
                monetTheme.contains(
                    """<item name="windowSplashScreenAnimatedIcon">@mipmap/splash_icon_bilipai_monet</item>"""
                ),
                "$themePath should use the dynamically colored Monet splash icon"
            )
        }

        val rows = readPngRgbaRows(
            loadResourceFile("mipmap-xxxhdpi/ic_launcher_bilipai_monet_foreground.png")
        )
        val width = rows.first().size / 4
        val opaquePoints = buildList {
            rows.forEachIndexed { y, row ->
                for (x in 0 until width) {
                    if (row[x * 4 + 3] != 0) add(x to y)
                }
            }
        }
        val artworkWidth = opaquePoints.maxOf { it.first } - opaquePoints.minOf { it.first } + 1
        val artworkHeight = opaquePoints.maxOf { it.second } - opaquePoints.minOf { it.second } + 1
        assertTrue(
            artworkWidth in 192..264 && artworkHeight in 192..264,
            "BiliPai Monet logo should remain inside Android's 48dp..66dp adaptive-icon safe zone"
        )
    }

    @Test
    fun splashIcons_shouldNotPackageDuplicateDrawableAssets() {
        listOf(
            "splash_icon_3d.png",
            "splash_icon_bilipai.png",
            "splash_icon_bilipai_pink.png",
            "splash_icon_bilipai_white.png",
            "splash_icon_bilipai_monet.png",
            "splash_icon_flat.png",
            "splash_icon_telegram_blue.png",
            "splash_icon_telegram_dark.png",
            "splash_icon_yuki.png",
            "splash_icon_anime.png",
            "splash_icon_headphone.png"
        ).forEach { fileName ->
            assertTrue(
                !resourcePathExists("drawable-nodpi/$fileName"),
                "$fileName should not be packaged separately; splash should reuse existing launcher mipmaps"
            )
        }
    }

    @Test
    fun retiredLauncherIcons_shouldNotKeepDedicatedResources() {
        val retiredResourceNames = listOf(
            "ic_launcher_anime",
            "ic_launcher_flat",
            "ic_launcher_flat_round",
            "ic_launcher_headphone",
            "ic_launcher_telegram_blue",
            "ic_launcher_telegram_blue_round",
            "ic_launcher_telegram_dark",
            "ic_launcher_telegram_dark_round"
        )
        val source = listOf(
            loadResourceText("../AndroidManifest.xml"),
            loadResourceText("values/themes.xml"),
            loadResourceText("values-night/themes.xml"),
            loadMainActivitySource(),
            loadMiniPlayerManagerSource()
        ).joinToString("\n")

        retiredResourceNames.forEach { resourceName ->
            assertTrue(
                !source.contains(resourceName),
                "$resourceName should not be referenced after the launcher option is retired"
            )
        }
        assertTrue(
            !Regex("""(?:@mipmap/|R\.mipmap\.)ic_launcher(?:[\"\s,)]|$)""").containsMatchIn(source),
            "retired Yuki launcher resource should not be referenced"
        )
    }

    @Test
    fun legacyLauncherBitmaps_shouldBeRgbaPngWithTransparentCorners() {
        val iconNames = listOf(
            "ic_launcher_blue_snow_maid.png",
            "ic_launcher_blue_snow_maid_round.png",
            "ic_launcher_blue_snow_maid_announcement.png",
            "ic_launcher_blue_snow_maid_announcement_round.png",
            "ic_launcher_blue_snow_maid_announcement_light.png",
            "ic_launcher_blue_snow_maid_announcement_light_round.png",
            "ic_launcher_blue_snow_maid_announcement_dark.png",
            "ic_launcher_blue_snow_maid_announcement_dark_round.png",
            "ic_launcher_blue_snow_maid_front.png",
            "ic_launcher_blue_snow_maid_front_round.png",
            "ic_launcher_3d.png",
            "ic_launcher_3d_round.png",
            "ic_launcher_bilipai.png",
            "ic_launcher_bilipai_round.png",
            "ic_launcher_bilipai_monet.png",
            "ic_launcher_bilipai_monet_round.png",
            "ic_launcher_bilipai_pink.png",
            "ic_launcher_bilipai_pink_round.png",
            "ic_launcher_bilipai_white.png",
            "ic_launcher_bilipai_white_round.png"
        )

        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { density ->
            iconNames.forEach { fileName ->
                val imageFile = loadResourceFile("mipmap-$density/$fileName")
                assertTrue(readPngHeader(imageFile).colorType == 6, "$density/$fileName should be an RGBA PNG")
                assertTrue(
                    readPngCornerAlphaValues(imageFile).all { it == 0 },
                    "$density/$fileName should not expose square bitmap corners when used as a fallback launcher icon"
                )
            }
        }
    }

    @Test
    fun launcherTargets_shouldReferenceDedicatedRoundIcons() {
        val manifest = loadResourceText("../AndroidManifest.xml")
        listOf(
            "MainActivitySplashIcon3D" to "ic_launcher_3d_round",
            "MainActivitySplashBiliPai" to "ic_launcher_bilipai_round",
            "MainActivitySplashBiliPaiPink" to "ic_launcher_bilipai_pink_round",
            "MainActivitySplashBiliPaiWhite" to "ic_launcher_bilipai_white_round",
            "MainActivitySplashBiliPaiMonet" to "ic_launcher_bilipai_monet_round"
        ).forEach { (activityName, roundIconName) ->
            val activityDeclaration = Regex(
                """<activity\s+android:name="\.$activityName".*?/>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(manifest)?.value

            assertTrue(
                activityDeclaration?.contains(
                    """android:roundIcon="@mipmap/$roundIconName"""
                ) == true,
                "$activityName should expose its dedicated round icon to system component resolvers"
            )
        }
    }

    @Test
    fun blueSnowMaidAdaptiveForegrounds_shouldKeepThemeAwareOuterShell() {
        listOf(
            "drawable/ic_launcher_blue_snow_maid_background.xml",
            "drawable/ic_launcher_blue_snow_maid_background_light.xml"
        ).forEach { resourcePath ->
            assertTrue(
                loadResourceText(resourcePath).contains("#FFFFFFFF"),
                "$resourcePath should keep a light outer field around the circular portrait"
            )
        }
        listOf(
            "drawable/ic_launcher_blue_snow_maid_background_dark.xml",
            "drawable-night/ic_launcher_blue_snow_maid_background.xml"
        ).forEach { resourcePath ->
            assertTrue(
                loadResourceText(resourcePath).contains("#FF090A0C"),
                "$resourcePath should keep a dark outer field around the circular portrait"
            )
        }
        assertTrue(
            loadResourceText("drawable/ic_launcher_blue_snow_maid_announcement_background.xml")
                .contains("#FFFFFFFF"),
            "The light announcement icon should keep its distinct white circular background"
        )
        listOf(
            "drawable/ic_launcher_blue_snow_maid_announcement_background_dark.xml",
            "drawable-night/ic_launcher_blue_snow_maid_announcement_background.xml"
        ).forEach { resourcePath ->
            assertTrue(
                loadResourceText(resourcePath).contains("#FF090A0C"),
                "$resourcePath should keep the announcement icon's distinct black circular background"
            )
        }

        listOf("ic_launcher_blue_snow_maid_foreground.png").forEach { fileName ->
            val rows = readPngRgbaRows(loadResourceFile("mipmap-xxxhdpi/$fileName"))
            val imageWidth = rows.first().size / 4
            val opaqueXs = buildList {
                rows.forEach { row ->
                    for (x in 0 until imageWidth) {
                        if (row[x * 4 + 3] != 0) add(x)
                    }
                }
            }
            val foregroundWidthRatio = (opaqueXs.max() - opaqueXs.min() + 1).toFloat() / imageWidth
            assertTrue(
                foregroundWidthRatio in 0.57f..0.59f,
                "$fileName should preserve the circular portrait and theme-aware outer field on rounded-square launchers"
            )
        }

        val frontRows = readPngRgbaRows(
            loadResourceFile("mipmap-xxxhdpi/ic_launcher_blue_snow_maid_front_foreground.png")
        )
        val frontWidth = frontRows.first().size / 4
        val frontOpaqueXs = buildList {
            frontRows.forEach { row ->
                for (x in 0 until frontWidth) {
                    if (row[x * 4 + 3] != 0) add(x)
                }
            }
        }
        val frontWidthRatio =
            (frontOpaqueXs.max() - frontOpaqueXs.min() + 1).toFloat() / frontWidth
        assertTrue(
            frontWidthRatio in 0.57f..0.59f,
            "Front maid foreground should preserve the same theme-aware outer field without being cropped"
        )

        val announcementRows = readPngRgbaRows(
            loadResourceFile("mipmap-xxxhdpi/ic_launcher_blue_snow_maid_announcement_foreground.png")
        )
        val announcementWidth = announcementRows.first().size / 4
        val announcementOpaqueXs = buildList {
            announcementRows.forEach { row ->
                for (x in 0 until announcementWidth) {
                    if (row[x * 4 + 3] != 0) add(x)
                }
            }
        }
        val announcementWidthRatio =
            (announcementOpaqueXs.max() - announcementOpaqueXs.min() + 1).toFloat() / announcementWidth
        assertTrue(
            announcementWidthRatio in 0.48f..0.50f,
            "Announcement foreground should leave comfortable breathing room inside Android's adaptive-icon safe zone"
        )

        listOf(
            "ic_launcher_blue_snow_maid_monochrome.png",
            "ic_launcher_blue_snow_maid_announcement_monochrome.png",
            "ic_launcher_blue_snow_maid_front_monochrome.png"
        ).forEach { fileName ->
            val rows = readPngRgbaRows(loadResourceFile("mipmap-xxxhdpi/$fileName"))
            val centerAlpha = rows[rows.size / 2][rows.first().size / 2 + 3]
            assertTrue(
                centerAlpha >= 240,
                "$fileName should keep the face in positive space so themed icons do not render a dark facial mask"
            )
        }

        val sideMaidRows = readPngRgbaRows(
            loadResourceFile("mipmap-xxxhdpi/ic_launcher_blue_snow_maid_monochrome.png")
        )
        fun negativeEyeArea(centerX: Int, centerY: Int): Int =
            (centerY - 19..centerY + 19).sumOf { y ->
                (centerX - 19..centerX + 19).count { x ->
                    sideMaidRows[y][x * 4 + 3] < 128
                }
            }
        val leftEyeArea = negativeEyeArea(centerX = 216, centerY = 204)
        val rightEyeArea = negativeEyeArea(centerX = 264, centerY = 216)
        assertTrue(
            leftEyeArea >= 600 &&
                rightEyeArea >= 600 &&
                leftEyeArea - rightEyeArea in -100..100,
            "The tilted maid themed icon should retain two balanced solid pupil shapes along the facial axis"
        )

        listOf(
            "mipmap-anydpi-v26/ic_launcher_blue_snow_maid_announcement.xml",
            "mipmap-anydpi-v26/ic_launcher_blue_snow_maid_announcement_round.xml",
            "mipmap-anydpi-v26/ic_launcher_blue_snow_maid_announcement_light.xml",
            "mipmap-anydpi-v26/ic_launcher_blue_snow_maid_announcement_light_round.xml",
            "mipmap-anydpi-v26/ic_launcher_blue_snow_maid_announcement_dark.xml",
            "mipmap-anydpi-v26/ic_launcher_blue_snow_maid_announcement_dark_round.xml",
            "mipmap-night-anydpi-v26/ic_launcher_blue_snow_maid_announcement.xml",
            "mipmap-night-anydpi-v26/ic_launcher_blue_snow_maid_announcement_round.xml"
        ).forEach { resourcePath ->
            assertTrue(
                loadResourceText(resourcePath).contains(
                    "<monochrome android:drawable=\"@mipmap/ic_launcher_blue_snow_maid_announcement_monochrome\" />"
                ),
                "$resourcePath should expose the announcement-specific Android themed icon"
            )
        }
    }

    @Test
    fun blueSnowMaidLauncherIcons_shouldUseThemeAwareAdaptiveShellsAndCircularFallbacks() {
        assertTrue(
            loadResourceText("drawable-night/ic_launcher_blue_snow_maid_background.xml")
                .contains("#FF090A0C"),
            "Dark mode adaptive icons should use the dark outer field around the circular portrait"
        )
        assertTrue(
            loadResourceText("drawable-night/ic_launcher_blue_snow_maid_announcement_background.xml")
                .contains("#FF090A0C"),
            "Announcement icon should keep its distinct black field in dark mode"
        )
        mapOf(
            "ic_launcher_blue_snow_maid" to "ic_launcher_blue_snow_maid_background_dark",
            "ic_launcher_blue_snow_maid_announcement" to
                "ic_launcher_blue_snow_maid_announcement_background_dark",
            "ic_launcher_blue_snow_maid_front" to "ic_launcher_blue_snow_maid_background_dark"
        ).forEach { (iconStem, backgroundStem) ->
            listOf("", "_round").forEach { suffix ->
                assertTrue(
                    loadResourceText("mipmap-night-anydpi-v26/$iconStem$suffix.xml")
                        .contains("@drawable/$backgroundStem"),
                    "$iconStem$suffix should remain adaptive in dark mode instead of falling back to a legacy PNG"
                )
            }
        }
        val announcementFallbackRows = readPngRgbaRows(
            loadResourceFile("mipmap-night-xxxhdpi/ic_launcher_blue_snow_maid_announcement_round.png")
        )
        val announcementCorner = announcementFallbackRows.first().take(4)
        assertTrue(
            announcementCorner[3] == 0,
            "Announcement fallback icon should be a circle instead of a square shell"
        )
        val announcementCenterTop =
            announcementFallbackRows[4].slice(96 * 4 until 96 * 4 + 4)
        assertTrue(
            announcementCenterTop[0] <= 16 && announcementCenterTop[1] <= 16 &&
                announcementCenterTop[2] <= 16 && announcementCenterTop[3] > 0,
            "The dark announcement fallback should reach its circular edge with black, not blue"
        )

        mapOf(
            "mdpi" to (48 to 108),
            "hdpi" to (72 to 162),
            "xhdpi" to (96 to 216),
            "xxhdpi" to (144 to 324),
            "xxxhdpi" to (192 to 432)
        ).forEach { (density, sizes) ->
            listOf(
                "ic_launcher_blue_snow_maid.png",
                "ic_launcher_blue_snow_maid_round.png",
                "ic_launcher_blue_snow_maid_announcement.png",
                "ic_launcher_blue_snow_maid_announcement_round.png",
                "ic_launcher_blue_snow_maid_front.png",
                "ic_launcher_blue_snow_maid_front_round.png"
            ).forEach { fileName ->
                val file = loadResourceFile("mipmap-night-$density/$fileName")
                val header = readPngHeader(file)
                assertTrue(header.width == sizes.first && header.height == sizes.first)
                assertTrue(header.colorType == 6 && readPngCornerAlphaValues(file).all { it == 0 })
            }
            listOf(
                "ic_launcher_blue_snow_maid_foreground.png",
                "ic_launcher_blue_snow_maid_announcement_foreground.png",
                "ic_launcher_blue_snow_maid_front_foreground.png"
            ).forEach { fileName ->
                val header = readPngHeader(loadResourceFile("mipmap-night-$density/$fileName"))
                assertTrue(header.width == sizes.second && header.height == sizes.second && header.colorType == 6)
            }
        }

        val darkRows = readPngRgbaRows(
            loadResourceFile("mipmap-night-xxxhdpi/ic_launcher_blue_snow_maid_front.png")
        )
        val edgeBluePixel = darkRows[4].slice(96 * 4 until 96 * 4 + 4)
        assertTrue(
            edgeBluePixel[3] > 0 && edgeBluePixel[2] > edgeBluePixel[0] + 80,
            "Dark fallback icons should reach their circular edge with blue artwork, not a black rounded rectangle"
        )
    }

    @Test
    fun fixedMaidAppearanceResources_shouldMatchTheirLightAndDarkMasters() {
        listOf(
            "ic_launcher_blue_snow_maid",
            "ic_launcher_blue_snow_maid_announcement",
            "ic_launcher_blue_snow_maid_front"
        )
            .forEach { stem ->
                listOf("", "_round", "_foreground").forEach { suffix ->
                    val lightMaster = loadResourceFile("mipmap-xxxhdpi/$stem$suffix.png")
                    val fixedLight = loadResourceFile("mipmap-xxxhdpi/${stem}_light$suffix.png")
                    val darkMaster = loadResourceFile("mipmap-night-xxxhdpi/$stem$suffix.png")
                    val fixedDark = loadResourceFile("mipmap-xxxhdpi/${stem}_dark$suffix.png")
                    assertTrue(lightMaster.readBytes().contentEquals(fixedLight.readBytes()))
                    assertTrue(darkMaster.readBytes().contentEquals(fixedDark.readBytes()))
                }
            }

        val lightAdaptive = loadResourceText("mipmap-anydpi-v26/ic_launcher_blue_snow_maid_light.xml")
        val darkAdaptive = loadResourceText("mipmap-anydpi-v26/ic_launcher_blue_snow_maid_dark.xml")
        assertTrue(lightAdaptive.contains("@drawable/ic_launcher_blue_snow_maid_background_light"))
        assertTrue(darkAdaptive.contains("@drawable/ic_launcher_blue_snow_maid_background_dark"))
        listOf(
            "ic_launcher_blue_snow_maid_announcement_light.xml",
            "ic_launcher_blue_snow_maid_announcement_light_round.xml"
        ).forEach { fileName ->
            assertTrue(
                loadResourceText("mipmap-anydpi-v26/$fileName")
                    .contains("@drawable/ic_launcher_blue_snow_maid_announcement_background"),
                "$fileName should keep the announcement icon's white background"
            )
        }
        listOf(
            "ic_launcher_blue_snow_maid_announcement_dark.xml",
            "ic_launcher_blue_snow_maid_announcement_dark_round.xml"
        ).forEach { fileName ->
            assertTrue(
                loadResourceText("mipmap-anydpi-v26/$fileName")
                    .contains("@drawable/ic_launcher_blue_snow_maid_announcement_background_dark"),
                "$fileName should keep the announcement icon's black background"
            )
        }
        assertTrue(
            loadResourceText("drawable/splash_icon_blue_snow_maid_light.xml")
                .contains("#FFFFFFFF")
        )
        assertTrue(
            loadResourceText("drawable/splash_icon_blue_snow_maid_dark.xml")
                .contains("#FF090A0C")
        )
        assertTrue(
            loadResourceText("drawable/splash_icon_blue_snow_maid_announcement_light.xml")
                .contains("#FFFFFFFF")
        )
        assertTrue(
            loadResourceText("drawable/splash_icon_blue_snow_maid_announcement_dark.xml")
                .contains("#FF090A0C")
        )
    }

    @Test
    fun launcherAliases_shouldBindMatchingSplashThemesForSelectedIcons() {
        val manifest = loadResourceText("../AndroidManifest.xml")

        mapOf(
            "MainActivityAliasBlueSnowMaid" to SplashAliasContract("MainActivitySplashBlueSnowMaid", "Theme.PureBiliBili.Splash.BlueSnowMaid", "ic_launcher_blue_snow_maid"),
            "MainActivityAliasBlueSnowMaidAnnouncement" to SplashAliasContract("MainActivitySplashBlueSnowMaidAnnouncement", "Theme.PureBiliBili.Splash.BlueSnowMaidAnnouncement", "ic_launcher_blue_snow_maid_announcement"),
            "MainActivityAliasBlueSnowMaidAnnouncementLight" to SplashAliasContract("MainActivitySplashBlueSnowMaidAnnouncementLight", "Theme.PureBiliBili.Splash.BlueSnowMaidAnnouncementLight", "ic_launcher_blue_snow_maid_announcement_light"),
            "MainActivityAliasBlueSnowMaidAnnouncementDark" to SplashAliasContract("MainActivitySplashBlueSnowMaidAnnouncementDark", "Theme.PureBiliBili.Splash.BlueSnowMaidAnnouncementDark", "ic_launcher_blue_snow_maid_announcement_dark"),
            "MainActivityAliasBlueSnowMaidFront" to SplashAliasContract("MainActivitySplashBlueSnowMaidFront", "Theme.PureBiliBili.Splash.BlueSnowMaidFront", "ic_launcher_blue_snow_maid_front"),
            "MainActivityAliasBlueSnowMaidLight" to SplashAliasContract("MainActivitySplashBlueSnowMaidLight", "Theme.PureBiliBili.Splash.BlueSnowMaidLight", "ic_launcher_blue_snow_maid_light"),
            "MainActivityAliasBlueSnowMaidDark" to SplashAliasContract("MainActivitySplashBlueSnowMaidDark", "Theme.PureBiliBili.Splash.BlueSnowMaidDark", "ic_launcher_blue_snow_maid_dark"),
            "MainActivityAliasBlueSnowMaidFrontLight" to SplashAliasContract("MainActivitySplashBlueSnowMaidFrontLight", "Theme.PureBiliBili.Splash.BlueSnowMaidFrontLight", "ic_launcher_blue_snow_maid_front_light"),
            "MainActivityAliasBlueSnowMaidFrontDark" to SplashAliasContract("MainActivitySplashBlueSnowMaidFrontDark", "Theme.PureBiliBili.Splash.BlueSnowMaidFrontDark", "ic_launcher_blue_snow_maid_front_dark"),
            "MainActivityAlias3DLauncher" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d"),
            "MainActivityAlias3D" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d"),
            "MainActivityAliasBiliPai" to SplashAliasContract("MainActivitySplashBiliPai", "Theme.PureBiliBili.Splash.BiliPai", "ic_launcher_bilipai"),
            "MainActivityAliasBiliPaiPink" to SplashAliasContract("MainActivitySplashBiliPaiPink", "Theme.PureBiliBili.Splash.BiliPaiPink", "ic_launcher_bilipai_pink"),
            "MainActivityAliasBiliPaiWhite" to SplashAliasContract("MainActivitySplashBiliPaiWhite", "Theme.PureBiliBili.Splash.BiliPaiWhite", "ic_launcher_bilipai_white"),
            "MainActivityAliasBiliPaiMonet" to SplashAliasContract("MainActivitySplashBiliPaiMonet", "Theme.PureBiliBili.Splash.BiliPaiMonet", "ic_launcher_bilipai_monet"),
            "MainActivityAliasFlat" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d"),
            "MainActivityAliasTelegramBlue" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d"),
            "MainActivityAliasDark" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d"),
            "MainActivityAliasYuki" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d"),
            "MainActivityAliasAnime" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d"),
            "MainActivityAliasHeadphone" to SplashAliasContract("MainActivitySplashIcon3D", "Theme.PureBiliBili.Splash.Icon3D", "ic_launcher_3d")
        ).forEach { (alias, contract) ->
            val aliasBlock = Regex(
                """<activity-alias\b(?=[^>]*android:name="\.$alias")[\s\S]*?</activity-alias>"""
            ).find(manifest)?.value.orEmpty()
            val targetActivityBlock = Regex(
                """<activity\b(?=[^>]*android:name="\.${contract.targetActivity}")[\s\S]*?(?:</activity>|/>)"""
            ).find(manifest)?.value.orEmpty()

            assertTrue(
                aliasBlock.contains("""android:targetActivity=".${contract.targetActivity}""""),
                "$alias should target ${contract.targetActivity} so Android splash can use the selected icon theme"
            )
            assertTrue(
                aliasBlock.contains("""android:icon="@mipmap/${contract.launcherIcon}""""),
                "$alias should keep the adaptive launcher icon for the home screen"
            )
            assertTrue(
                targetActivityBlock.contains("""android:theme="@style/${contract.theme}""""),
                "${contract.targetActivity} should bind ${contract.theme} so Android splash follows the selected launcher icon"
            )
            assertTrue(
                targetActivityBlock.contains("""android:icon="${contract.splashActivityIcon}""""),
                "${contract.targetActivity} should use the selected adaptive launcher icon"
            )
            assertTrue(
                targetActivityBlock.contains("""android:roundIcon="${contract.splashActivityRoundIcon}""""),
                "${contract.targetActivity} should expose the selected adaptive round icon"
            )
        }
    }

    @Test
    fun noIconLauncherAliases_shouldKeepLauncherIconButUseTransparentSplashTheme() {
        val manifest = loadResourceText("../AndroidManifest.xml")
        val lightThemes = loadResourceText("values/themes.xml")
        val nightThemes = loadResourceText("values-night/themes.xml")

        assertTrue(
            lightThemes.contains("""<style name="Theme.PureBiliBili.Splash.NoIcon" parent="Theme.PureBiliBili">""") &&
                lightThemes.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/splash_no_icon</item>"""),
            "Light no-icon splash theme should use the transparent splash icon drawable"
        )
        assertTrue(
            nightThemes.contains("""<style name="Theme.PureBiliBili.Splash.NoIcon" parent="Theme.PureBiliBili">""") &&
                nightThemes.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/splash_no_icon</item>"""),
            "Night no-icon splash theme should use the transparent splash icon drawable"
        )

        listOf(
            "MainActivityAliasBlueSnowMaidNoIcon" to "ic_launcher_blue_snow_maid",
            "MainActivityAliasBlueSnowMaidAnnouncementNoIcon" to "ic_launcher_blue_snow_maid_announcement",
            "MainActivityAliasBlueSnowMaidAnnouncementLightNoIcon" to "ic_launcher_blue_snow_maid_announcement_light",
            "MainActivityAliasBlueSnowMaidAnnouncementDarkNoIcon" to "ic_launcher_blue_snow_maid_announcement_dark",
            "MainActivityAliasBlueSnowMaidFrontNoIcon" to "ic_launcher_blue_snow_maid_front",
            "MainActivityAliasBlueSnowMaidLightNoIcon" to "ic_launcher_blue_snow_maid_light",
            "MainActivityAliasBlueSnowMaidDarkNoIcon" to "ic_launcher_blue_snow_maid_dark",
            "MainActivityAliasBlueSnowMaidFrontLightNoIcon" to "ic_launcher_blue_snow_maid_front_light",
            "MainActivityAliasBlueSnowMaidFrontDarkNoIcon" to "ic_launcher_blue_snow_maid_front_dark",
            "MainActivityAlias3DNoIcon" to "ic_launcher_3d",
            "MainActivityAliasBiliPaiNoIcon" to "ic_launcher_bilipai",
            "MainActivityAliasBiliPaiPinkNoIcon" to "ic_launcher_bilipai_pink",
            "MainActivityAliasBiliPaiWhiteNoIcon" to "ic_launcher_bilipai_white",
            "MainActivityAliasBiliPaiMonetNoIcon" to "ic_launcher_bilipai_monet",
            "MainActivityAliasFlatNoIcon" to "ic_launcher_3d",
            "MainActivityAliasTelegramBlueNoIcon" to "ic_launcher_3d",
            "MainActivityAliasDarkNoIcon" to "ic_launcher_3d",
            "MainActivityAliasYukiNoIcon" to "ic_launcher_3d",
            "MainActivityAliasAnimeNoIcon" to "ic_launcher_3d",
            "MainActivityAliasHeadphoneNoIcon" to "ic_launcher_3d"
        ).forEach { (alias, launcherIcon) ->
            val aliasBlock = Regex(
                """<activity-alias\b(?=[^>]*android:name="\.$alias")[\s\S]*?</activity-alias>"""
            ).find(manifest)?.value.orEmpty()

            assertTrue(
                aliasBlock.contains("""android:targetActivity=".MainActivitySplashNoIcon""""),
                "$alias should target the transparent splash activity"
            )
            assertTrue(
                aliasBlock.contains("""android:icon="@mipmap/$launcherIcon""""),
                "$alias should keep the selected launcher icon on the home screen"
            )
        }
    }

    @Test
    fun splashFlyout_shouldReuseLauncherIconForSelectedLauncherComponent() {
        mapOf(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaid" to R.mipmap.ic_launcher_blue_snow_maid,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaid" to R.drawable.splash_icon_blue_snow_maid,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncement" to R.mipmap.ic_launcher_blue_snow_maid_announcement,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidAnnouncement" to R.drawable.splash_icon_blue_snow_maid_announcement,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncementLight" to R.mipmap.ic_launcher_blue_snow_maid_announcement_light,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidAnnouncementLight" to R.drawable.splash_icon_blue_snow_maid_announcement_light,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncementDark" to R.mipmap.ic_launcher_blue_snow_maid_announcement_dark,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidAnnouncementDark" to R.drawable.splash_icon_blue_snow_maid_announcement_dark,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidFront" to R.mipmap.ic_launcher_blue_snow_maid_front,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidFront" to R.drawable.splash_icon_blue_snow_maid_front,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidLight" to R.mipmap.ic_launcher_blue_snow_maid_light,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidLight" to R.drawable.splash_icon_blue_snow_maid_light,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidDark" to R.mipmap.ic_launcher_blue_snow_maid_dark,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidDark" to R.drawable.splash_icon_blue_snow_maid_dark,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidFrontLight" to R.mipmap.ic_launcher_blue_snow_maid_front_light,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidFrontLight" to R.drawable.splash_icon_blue_snow_maid_front_light,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidFrontDark" to R.mipmap.ic_launcher_blue_snow_maid_front_dark,
            "com.android.purebilibili.MainActivitySplashBlueSnowMaidFrontDark" to R.drawable.splash_icon_blue_snow_maid_front_dark,
            "com.android.purebilibili.MainActivityAlias3DLauncher" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivitySplashIcon3D" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivityAliasBiliPai" to R.mipmap.ic_launcher_bilipai,
            "com.android.purebilibili.MainActivitySplashBiliPai" to R.mipmap.ic_launcher_bilipai,
            "com.android.purebilibili.MainActivityAliasBiliPaiPink" to R.mipmap.ic_launcher_bilipai_pink,
            "com.android.purebilibili.MainActivityAliasBiliPaiWhite" to R.mipmap.ic_launcher_bilipai_white,
            "com.android.purebilibili.MainActivityAliasBiliPaiMonet" to R.mipmap.ic_launcher_bilipai_monet,
            "com.android.purebilibili.MainActivitySplashBiliPaiMonet" to R.mipmap.splash_icon_bilipai_monet,
            "com.android.purebilibili.MainActivityAliasFlat" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivityAliasTelegramBlue" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivityAliasDark" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivityAliasYuki" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivityAliasAnime" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivityAliasHeadphone" to R.mipmap.ic_launcher_3d,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidNoIcon" to R.mipmap.ic_launcher_blue_snow_maid,
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidFrontNoIcon" to R.mipmap.ic_launcher_blue_snow_maid_front,
            "com.android.purebilibili.MainActivityAlias3DNoIcon" to R.mipmap.ic_launcher_3d
        ).forEach { (className, iconResId) ->
            assertTrue(
                resolveSplashIconResIdForComponentClassName(className) == iconResId,
                "$className should resolve to the matching launcher mipmap"
            )
        }
    }

    @Test
    fun blueSnowMaid_shouldBeManifestDefaultAndPlayStoreAssetShouldBeValid() {
        val manifest = loadResourceText("../AndroidManifest.xml")
        val defaultAliasBlock = Regex(
            """<activity-alias\b(?=[^>]*android:name="\.MainActivityAliasBlueSnowMaid")[\s\S]*?</activity-alias>"""
        ).find(manifest)?.value.orEmpty()
        val legacyDefaultAliasBlock = Regex(
            """<activity-alias\b(?=[^>]*android:name="\.MainActivityAlias3DLauncher")[\s\S]*?</activity-alias>"""
        ).find(manifest)?.value.orEmpty()
        val playStoreIcon = listOf(
            File("app/src/main/ic_launcher-playstore.png"),
            File("src/main/ic_launcher-playstore.png")
        ).firstOrNull { it.exists() } ?: error("Cannot locate ic_launcher-playstore.png")
        val playStoreHeader = readPngHeader(playStoreIcon)

        assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher_blue_snow_maid"""))
        assertTrue(manifest.contains("""android:roundIcon="@mipmap/ic_launcher_blue_snow_maid_round"""))
        assertTrue(defaultAliasBlock.contains("""android:enabled="true"""))
        assertTrue(legacyDefaultAliasBlock.contains("""android:enabled="false"""))
        assertTrue(playStoreHeader.width == 512 && playStoreHeader.height == 512)
        assertTrue(playStoreHeader.colorType == 6, "Play Store icon should be an RGBA PNG")
        assertTrue(playStoreIcon.length() <= 1_024L * 1_024L, "Play Store icon should stay within 1 MB")
    }

    @Test
    fun appIconSwitch_shouldNotRequestAppRestartOrRecreate() {
        val settingsViewModelSource = loadSettingsViewModelSource()
        val launcherAliasSwitchBody = Regex(
            """private suspend fun applyLauncherAliasForCurrentSplashIconSetting\([\s\S]*?\n    \}"""
        ).find(settingsViewModelSource)?.value ?: Regex(
            """fun setAppIcon\(iconKey: String\) \{[\s\S]*?\n    \}"""
        ).find(settingsViewModelSource)?.value.orEmpty()

        assertTrue(
            launcherAliasSwitchBody.contains("PackageManager.DONT_KILL_APP"),
            "Icon switching should request DONT_KILL_APP to avoid reloading the running app"
        )
        assertTrue(
            !launcherAliasSwitchBody.contains("restartApp") && !launcherAliasSwitchBody.contains(".recreate("),
            "Icon switching should not explicitly restart or recreate the current app UI"
        )
    }

    @Test
    fun mainActivity_shouldUseCachedAppLanguageAsComposeInitialValue() {
        val mainActivitySource = loadMainActivitySource()
        val themeSource = loadThemeSource()

        assertTrue(
            mainActivitySource.contains(".getAppThemeSettings(context)"),
            "MainActivity should collect startup theme settings through one DataStore Flow"
        )
        assertTrue(
            mainActivitySource.contains("initialValue = SettingsManager.getInitialAppThemeSettings(context)"),
            "MainActivity should bootstrap appLanguage from cached settings to avoid locale flip-flop during recreation"
        )
        assertTrue(
            themeSource.contains("ThemeController("),
            "Theme root should build a miuix ThemeController"
        )
        assertTrue(
            mainActivitySource.contains("appThemeSettings.uiStyle"),
            "MainActivity should read the two-value AppUiStyle directly from startup theme settings"
        )
        assertTrue(
            mainActivitySource.contains("AppThemeSettings(") ||
                mainActivitySource.contains("getInitialAppThemeSettings(context)"),
            "MainActivity should bootstrap first install with the MD3 preset"
        )
    }

    private fun loadResourceFile(resourcePath: String): File {
        val roots = listOf(
            File("app/src/main/res"), File("src/main/res"),
            File("brand-motion/src/main/res"), File("../brand-motion/src/main/res"),
        )
        roots.map { File(it, resourcePath) }.firstOrNull { it.isFile }?.let { return it }
        // Keep all pixel/density/theme assertions, following resource aliases just as Android does.
        if (resourcePath.startsWith("mipmap") && resourcePath.endsWith(".png")) {
            val directory = resourcePath.substringBefore('/')
            val name = resourcePath.substringAfter('/').removeSuffix(".png")
            val density = directory.split('-').firstOrNull { it.endsWith("dpi") }
            for (root in roots) {
                val aliases = File(root, directory.replaceFirst("mipmap", "values") + "/launcher_image_aliases.xml")
                if (!aliases.isFile) continue
                val items = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(aliases).getElementsByTagName("item")
                for (index in 0 until items.length) {
                    val item = items.item(index) as org.w3c.dom.Element
                    if (item.getAttribute("name") != name || item.getAttribute("type") != "mipmap") continue
                    val target = item.textContent.trim().removePrefix("@mipmap/")
                    val targetDirectory = if (density == null) "mipmap" else "mipmap-$density"
                    return roots.map { File(it, "$targetDirectory/$target.png") }.firstOrNull { it.isFile }
                        ?: error("Missing bitmap target for $resourcePath")
                }
            }
        }
        error("Cannot locate $resourcePath from ${File(".").absolutePath}")
    }

    private fun resourcePathExists(resourcePath: String): Boolean =
        runCatching { loadResourceFile(resourcePath) }.isSuccess

    private fun loadResourceText(resourcePath: String): String {
        return loadResourceFile(resourcePath).readText()
    }

    private data class ImageSize(val width: Int, val height: Int)

    private data class PngHeader(val width: Int, val height: Int, val colorType: Int)

    private data class SplashAliasContract(
        val targetActivity: String,
        val theme: String,
        val launcherIcon: String,
        val splashActivityIcon: String = "@mipmap/$launcherIcon",
        val splashActivityRoundIcon: String = "@mipmap/${launcherIcon}_round"
    )

    private fun readPngHeader(file: File): PngHeader {
        val bytes = file.readBytes()
        assertTrue(
            bytes.size >= 24 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() &&
                bytes[3] == 'G'.code.toByte(),
            "${file.name} should be a real PNG file"
        )
        return PngHeader(
            width = bytes.readBigEndianInt(offset = 16),
            height = bytes.readBigEndianInt(offset = 20),
            colorType = bytes[25].toInt() and 0xFF
        )
    }

    private fun readPngCornerAlphaValues(file: File): List<Int> {
        val rows = readPngRgbaRows(file)
        val width = rows.first().size / 4
        val height = rows.size
        fun alphaAt(x: Int, y: Int): Int = rows[y][x * 4 + 3]
        return listOf(
            alphaAt(0, 0),
            alphaAt(width - 1, 0),
            alphaAt(0, height - 1),
            alphaAt(width - 1, height - 1)
        )
    }

    private fun readPngRgbaRows(file: File): List<IntArray> {
        val bytes = file.readBytes()
        var offset = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        val idat = ByteArrayOutputStream()

        while (offset + 12 <= bytes.size) {
            val length = bytes.readBigEndianInt(offset)
            val type = String(bytes, offset + 4, 4)
            val dataOffset = offset + 8
            when (type) {
                "IHDR" -> {
                    width = bytes.readBigEndianInt(dataOffset)
                    height = bytes.readBigEndianInt(dataOffset + 4)
                    bitDepth = bytes[dataOffset + 8].toInt() and 0xFF
                    colorType = bytes[dataOffset + 9].toInt() and 0xFF
                }
                "IDAT" -> idat.write(bytes, dataOffset, length)
                "IEND" -> break
            }
            offset += 12 + length
        }

        assertTrue(bitDepth == 8 && colorType == 6, "${file.name} should be an 8-bit RGBA PNG")

        val inflated = InflaterInputStream(idat.toByteArray().inputStream()).readBytes()
        return decodePngRgbaRows(
            inflated = inflated,
            width = width,
            height = height
        )
    }

    private fun readPngOrJpegSize(file: File): ImageSize {
        val bytes = file.readBytes()
        if (bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()
        ) {
            return ImageSize(
                width = bytes.readBigEndianInt(offset = 16),
                height = bytes.readBigEndianInt(offset = 20)
            )
        }

        if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var offset = 2
            while (offset + 9 < bytes.size) {
                while (offset < bytes.size && bytes[offset] != 0xFF.toByte()) {
                    offset++
                }
                if (offset + 3 >= bytes.size) break
                val marker = bytes[offset + 1].toInt() and 0xFF
                offset += 2
                if (marker in 0xD0..0xD9 || marker == 0x01) continue
                val segmentLength = bytes.readUnsignedShort(offset)
                if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)) {
                    return ImageSize(
                        width = bytes.readUnsignedShort(offset + 5),
                        height = bytes.readUnsignedShort(offset + 3)
                    )
                }
                offset += segmentLength
            }
        }

        error("Unsupported image header for ${file.path}")
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 8) or
            (this[offset + 1].toInt() and 0xFF)
    }

    private fun decodePngRgbaRows(
        inflated: ByteArray,
        width: Int,
        height: Int
    ): List<IntArray> {
        val bytesPerPixel = 4
        val stride = width * bytesPerPixel
        var inputOffset = 0
        var previous = IntArray(stride)
        return List(height) {
            val filter = inflated[inputOffset++].toInt() and 0xFF
            val row = IntArray(stride)
            for (i in 0 until stride) {
                val raw = inflated[inputOffset++].toInt() and 0xFF
                val left = if (i >= bytesPerPixel) row[i - bytesPerPixel] else 0
                val up = previous[i]
                val upLeft = if (i >= bytesPerPixel) previous[i - bytesPerPixel] else 0
                row[i] = when (filter) {
                    0 -> raw
                    1 -> (raw + left) and 0xFF
                    2 -> (raw + up) and 0xFF
                    3 -> (raw + ((left + up) / 2)) and 0xFF
                    4 -> (raw + paethPredictor(left, up, upLeft)) and 0xFF
                    else -> error("Unsupported PNG filter $filter")
                }
            }
            previous = row
            row
        }
    }

    private fun readVisibleDarkOuterEdgePngPixelCount(file: File): Int {
        val bytes = file.readBytes()
        var offset = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        val idat = ByteArrayOutputStream()

        while (offset + 12 <= bytes.size) {
            val length = bytes.readBigEndianInt(offset)
            val type = String(bytes, offset + 4, 4)
            val dataOffset = offset + 8
            when (type) {
                "IHDR" -> {
                    width = bytes.readBigEndianInt(dataOffset)
                    height = bytes.readBigEndianInt(dataOffset + 4)
                    bitDepth = bytes[dataOffset + 8].toInt() and 0xFF
                    colorType = bytes[dataOffset + 9].toInt() and 0xFF
                }
                "IDAT" -> idat.write(bytes, dataOffset, length)
                "IEND" -> break
            }
            offset += 12 + length
        }

        assertTrue(bitDepth == 8 && colorType == 6, "${file.name} should be an 8-bit RGBA PNG")

        val inflated = InflaterInputStream(idat.toByteArray().inputStream()).readBytes()
        val rows = decodePngRgbaRows(
            inflated = inflated,
            width = width,
            height = height
        )
        var darkPixels = 0
        val outerEdgeInset = minOf(width, height) / 8

        rows.forEachIndexed { y, row ->
            for (x in 0 until width) {
                if (
                    x >= outerEdgeInset &&
                    x < width - outerEdgeInset &&
                    y >= outerEdgeInset &&
                    y < height - outerEdgeInset
                ) {
                    continue
                }
                val pixelOffset = x * 4
                val r = row[pixelOffset]
                val g = row[pixelOffset + 1]
                val b = row[pixelOffset + 2]
                val a = row[pixelOffset + 3]
                if (a > 0 && maxOf(r, g, b) < 150) {
                    darkPixels++
                }
            }
        }

        return darkPixels
    }

    private fun paethPredictor(left: Int, up: Int, upLeft: Int): Int {
        val estimate = left + up - upLeft
        val leftDistance = kotlin.math.abs(estimate - left)
        val upDistance = kotlin.math.abs(estimate - up)
        val upLeftDistance = kotlin.math.abs(estimate - upLeft)
        return when {
            leftDistance <= upDistance && leftDistance <= upLeftDistance -> left
            upDistance <= upLeftDistance -> up
            else -> upLeft
        }
    }

    private fun splashDrawableVectorExists(): Boolean {
        return listOf(
            File("app/src/main/res/drawable/ic_launcher_bilipai_foreground.xml"),
            File("src/main/res/drawable/ic_launcher_bilipai_foreground.xml")
        ).any { it.exists() }
    }

    private fun loadMainActivitySource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/android/purebilibili/MainActivity.kt"),
            File("src/main/java/com/android/purebilibili/MainActivity.kt")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate MainActivity.kt from ${File(".").absolutePath}")
        return sourceFile.readText()
    }

    private fun loadMiniPlayerManagerSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/video/player/MiniPlayerManager.kt"),
            File("src/main/java/com/android/purebilibili/feature/video/player/MiniPlayerManager.kt")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate MiniPlayerManager.kt from ${File(".").absolutePath}")
        return sourceFile.readText()
    }

    private fun loadThemeSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/android/purebilibili/core/theme/Theme.kt"),
            File("src/main/java/com/android/purebilibili/core/theme/Theme.kt")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate Theme.kt from ${File(".").absolutePath}")
        return sourceFile.readText()
    }

    private fun loadSettingsViewModelSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/SettingsViewModel.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/SettingsViewModel.kt")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate SettingsViewModel.kt from ${File(".").absolutePath}")
        return sourceFile.readText()
    }
}
