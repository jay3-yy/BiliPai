package com.android.purebilibili.feature.home.components.cards

import kotlin.test.Test
import kotlin.test.assertTrue

class VideoCardCoverColorConcurrencyPolicyTest {

    @Test
    fun `extraction allows more than one in flight`() {
        // 回归护栏：单 Mutex 会把「磁盘读 + 解码 + Palette 聚类」完全串行化，
        // 回前台后几十张卡片的队列要几百毫秒才排空，掉帧窗口被整体拉长。
        assertTrue(COVER_COLOR_EXTRACTION_PARALLELISM >= 2)
        // 但也不能放开太多：Palette 聚类是 CPU 密集的，并行度过高会挤占渲染预算。
        assertTrue(COVER_COLOR_EXTRACTION_PARALLELISM <= 4)
    }
}
