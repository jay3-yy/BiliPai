package com.android.purebilibili.core.store

import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconKeyNormalizerTest {

    @Test
    fun normalizeAppIconKey_mapsLegacyKeysToCanonicalKeys() {
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("蓝雪女仆"))
        assertEquals("icon_blue_snow_maid_announcement", normalizeAppIconKey("蓝雪女仆·喇叭"))
        assertEquals("icon_blue_snow_maid_front", normalizeAppIconKey("蓝雪女仆·正面"))
        assertEquals("icon_bilipai", normalizeAppIconKey("BiliPai"))
        assertEquals("icon_bilipai_pink", normalizeAppIconKey("BiliPai Pink"))
        assertEquals("icon_bilipai_white", normalizeAppIconKey("BiliPai 白"))
        assertEquals("icon_bilipai_monet", normalizeAppIconKey("bilipai_monet"))
    }

    @Test
    fun normalizeAppIconKey_fallsBackToDefaultForUnknownOrBlankValues() {
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey(""))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("   "))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("non-existent"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("icon_retro"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Flat Material"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Blue"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Neon"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Pink"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Telegram Purple"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Green"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Telegram Blue Coin"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Yuki"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Anime"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Headphone"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Flat"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Telegram Blue"))
        assertEquals("icon_blue_snow_maid", normalizeAppIconKey("Telegram Dark"))
    }

    @Test
    fun normalizeAppIconKey_preservesExistingCanonicalChoices() {
        assertEquals("icon_blue_snow_maid_front", normalizeAppIconKey("icon_blue_snow_maid_front"))
        assertEquals("icon_blue_snow_maid_announcement", normalizeAppIconKey("icon_blue_snow_maid_announcement"))
        assertEquals("icon_3d", normalizeAppIconKey("icon_3d"))
        assertEquals("icon_bilipai", normalizeAppIconKey("icon_bilipai"))
        assertEquals("icon_bilipai_pink", normalizeAppIconKey("icon_bilipai_pink"))
        assertEquals("icon_bilipai_white", normalizeAppIconKey("icon_bilipai_white"))
        assertEquals("icon_bilipai_monet", normalizeAppIconKey("icon_bilipai_monet"))
    }

    @Test
    fun resolveAppIconAppearance_defaultsUnknownValuesToFollowSystem() {
        assertEquals(AppIconAppearance.FOLLOW_SYSTEM, resolveAppIconAppearance(0))
        assertEquals(AppIconAppearance.LIGHT, resolveAppIconAppearance(1))
        assertEquals(AppIconAppearance.DARK, resolveAppIconAppearance(2))
        assertEquals(AppIconAppearance.FOLLOW_SYSTEM, resolveAppIconAppearance(99))
    }

    @Test
    fun supportsAppIconAppearance_forMaidAndPlayWindowArtwork() {
        assertEquals(true, supportsAppIconAppearance("icon_blue_snow_maid"))
        assertEquals(true, supportsAppIconAppearance("icon_blue_snow_maid_announcement"))
        assertEquals(true, supportsAppIconAppearance("icon_blue_snow_maid_front"))
        assertEquals(true, supportsAppIconAppearance("icon_3d"))
        assertEquals(false, supportsAppIconAppearance("icon_bilipai"))
    }
}
