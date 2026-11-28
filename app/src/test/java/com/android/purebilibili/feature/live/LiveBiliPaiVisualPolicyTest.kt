package com.android.purebilibili.feature.live

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.CompactCapsuleChromeSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveBiliPaiVisualPolicyTest {

    @Test
    fun `home metrics mirror BiliPai live constants`() {
        val metrics = resolveLiveBiliPaiHomeMetrics()

        assertEquals(16, metrics.safeSpaceDp)
        assertEquals(8, metrics.cardSpaceDp)
        assertEquals(12, metrics.cardRadiusDp)
        assertEquals(16f / 10f, metrics.coverAspectRatio)
        assertEquals(45, metrics.followAvatarSizeDp)
        assertEquals(70, metrics.followItemExtentDp)
    }

    @Test
    fun `live visual spec preserves chrome presentation density`() {
        val ios = resolveLiveVisualSpec(AppTopTabPresentation.MOVING_CAPSULE)
        val md3 = resolveLiveVisualSpec(AppTopTabPresentation.MATERIAL_UNDERLINE)
        val miuix = resolveLiveVisualSpec(AppTopTabPresentation.TONAL_CAPSULE)

        assertEquals(16, ios.homeMetrics.safeSpaceDp)
        assertEquals(16, md3.homeMetrics.safeSpaceDp)
        assertEquals(16, miuix.homeMetrics.safeSpaceDp)
        assertEquals(8, ios.homeMetrics.cardSpaceDp)
        assertEquals(8, md3.homeMetrics.cardSpaceDp)
        assertEquals(8, miuix.homeMetrics.cardSpaceDp)
        assertEquals(90, ios.roomCardDetailsMinHeightDp)
        assertEquals(90, md3.roomCardDetailsMinHeightDp)
        assertEquals(90, miuix.roomCardDetailsMinHeightDp)
        assertEquals(30, ios.playerButtonTouchTargetDp)
        assertEquals(30, md3.playerButtonTouchTargetDp)
        assertEquals(30, miuix.playerButtonTouchTargetDp)
        assertEquals(30, ios.playerButtonVisualSizeDp)
        assertEquals(30, md3.playerButtonVisualSizeDp)
        assertEquals(30, miuix.playerButtonVisualSizeDp)
    }

    @Test
    fun `live grids use available width across device classes`() {
        assertEquals(2, resolveLiveBiliPaiGridColumns(widthDp = 390, isTabletLayout = false))
        assertEquals(1, resolveLiveBiliPaiGridColumns(widthDp = 220, isTabletLayout = false))
        assertEquals(3, resolveLiveBiliPaiGridColumns(widthDp = 720, isTabletLayout = false))
        assertEquals(3, resolveLiveBiliPaiGridColumns(widthDp = 720, isTabletLayout = true))
        assertEquals(5, resolveLiveBiliPaiGridColumns(widthDp = 1100, isTabletLayout = true))
        assertEquals(5, resolveLiveBiliPaiGridColumns(widthDp = 1200, isTabletLayout = true))
    }

    @Test
    fun `chip colors use theme accent for selected home category`() {
        val colors = resolveLiveBiliPaiChipColors(
            selectedContainer = Color(0xFF8FD5FF),
            selectedContent = Color(0xFF001F2A),
            unselectedContent = Color(0xFF49454F)
        )

        assertEquals(Color(0xFF8FD5FF), colors.selectedContainerColor)
        assertEquals(Color(0xFF001F2A), colors.selectedContentColor)
        assertEquals(Color.Transparent, colors.unselectedContainerColor)
        assertEquals(Color(0xFF49454F), colors.unselectedContentColor)
    }

    @Test
    fun `vertical overlay chat has a translucent black backing`() {
        val dark = resolveLiveBiliPaiChatBubbleTokens(isOverlay = true, isDark = true)
        val light = resolveLiveBiliPaiChatBubbleTokens(isOverlay = true, isDark = false)

        assertEquals(14, dark.cornerRadiusDp)
        assertEquals(10, dark.horizontalPaddingDp)
        assertEquals(4, dark.verticalPaddingDp)
        assertEquals(14, dark.fontSizeSp)
        assertEquals(0.4f, dark.backgroundAlpha)
        assertEquals(0.90f, dark.nameAlpha)
        assertEquals(0.4f, light.backgroundAlpha)
        assertEquals(0.90f, light.nameAlpha)
    }

    @Test
    fun `blank live danmaku is hidden unless it carries an image emoticon`() {
        assertEquals(false, shouldRenderLiveDanmaku(text = "", emoticonUrl = null))
        assertEquals(false, shouldRenderLiveDanmaku(text = "   ", emoticonUrl = ""))
        assertEquals(true, shouldRenderLiveDanmaku(text = "", emoticonUrl = "https://example.com/e.png"))
        assertEquals(true, shouldRenderLiveDanmaku(text = "赛事", emoticonUrl = null))
    }

    @Test
    fun `chat only uses image emoticon branch for non blank urls`() {
        assertEquals(false, shouldRenderLiveDanmakuImageEmoticon(null))
        assertEquals(false, shouldRenderLiveDanmakuImageEmoticon(""))
        assertEquals(false, shouldRenderLiveDanmakuImageEmoticon("   "))
        assertEquals(true, shouldRenderLiveDanmakuImageEmoticon("https://example.com/e.png"))
    }


    @Test
    fun `interaction segmented control follows android native variants`() {
        val md3 = resolveLiveInteractionSegmentedControlSpec(
            compactChrome(
                primaryHeightDp = 56,
                compactChipHeightDp = 28,
                chipHorizontalPaddingDp = 16,
                standardGapDp = 12,
            ),
        )
        val miuix = resolveLiveInteractionSegmentedControlSpec(
            compactChrome(
                primaryHeightDp = 48,
                compactChipHeightDp = 28,
                chipHorizontalPaddingDp = 12,
                standardGapDp = 8,
            ),
        )

        assertEquals(56, md3.heightDp)
        assertEquals(16, md3.horizontalPaddingDp)
        assertEquals(48, miuix.heightDp)
        assertEquals(12, miuix.horizontalPaddingDp)
    }

    @Test
    fun `live overlay controls keep compact button density`() {
        val chatInput = resolveLiveChatInputVisualSpec()
        val playerControl = resolveLivePlayerControlVisualSpec()
        val sheet = resolveLiveSheetVisualSpec()

        assertEquals(30, chatInput.controlSizeDp)
        assertEquals(30, chatInput.sendButtonSizeDp)
        assertEquals(40, playerControl.rowHeightDp)
        assertEquals(420, sheet.emoticonListMaxHeightDp)
        assertEquals(360, sheet.contributionListMaxHeightDp)
    }

    @Test
    fun `live room backdrop and input alpha mirror BiliPai transparent stack`() {
        val tokens = resolveLiveBiliPaiRoomColorTokens(
            inputOverlayColor = Color(0xFFDDE1E6),
            inputContentColor = Color(0xFFE6E1E5)
        )

        assertEquals(Color.Black, tokens.baseBackgroundColor)
        assertEquals(0.60f, tokens.backdropImageAlpha)
        assertEquals(0.10f, tokens.inputContainerAlpha)
        assertEquals(Color(0xFFDDE1E6), tokens.inputOverlayColor)
        assertEquals(Color(0xFFE6E1E5), tokens.inputContentColor)
    }

    @Test
    fun `room grid measures its parent space and distributes pixel remainders`() {
        val cells = LiveRoomGridCells()
        with(cells) {
            with(androidx.compose.ui.unit.Density(1f)) {
                assertEquals(listOf(179, 179), calculateCrossAxisCellSizes(366, 8))
                assertEquals(listOf(220), calculateCrossAxisCellSizes(220, 8))
            }
            with(androidx.compose.ui.unit.Density(2f)) {
                assertEquals(listOf(359, 358), calculateCrossAxisCellSizes(733, 16))
            }
        }
    }

    private fun compactChrome(
        primaryHeightDp: Int,
        compactChipHeightDp: Int,
        chipHorizontalPaddingDp: Int,
        standardGapDp: Int,
    ) = CompactCapsuleChromeSpec(
        primaryHeightDp = primaryHeightDp,
        secondaryButtonSizeDp = 48,
        chipHeightDp = 32,
        compactChipHeightDp = compactChipHeightDp,
        primaryCornerRadiusDp = 16,
        secondaryButtonCornerRadiusDp = 16,
        chipCornerRadiusDp = 16,
        compactChipCornerRadiusDp = 14,
        iconSizeDp = 20,
        smallIconSizeDp = 16,
        inputHorizontalPaddingDp = 12,
        chipHorizontalPaddingDp = chipHorizontalPaddingDp,
        compactChipHorizontalPaddingDp = 10,
        standardGapDp = standardGapDp,
    )
}
