package com.android.purebilibili.data.model

import com.android.purebilibili.data.repository.ContentRedirectException
import com.android.purebilibili.data.repository.ContentRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VideoLoadErrorTest {
    @Test
    fun `detail errors retain their api classification and retry policy`() {
        val cases = listOf(
            ContentRequestException(-404, "啥都木有") to VideoLoadError.VideoNotFound,
            ContentRequestException(-10403, "当前地区不可用") to VideoLoadError.RegionRestricted,
            ContentRequestException(-10403, "大会员专享") to VideoLoadError.VipRequired
        )
        for ((exception, expected) in cases) {
            val error = VideoLoadError.fromException(exception)
            assertEquals(expected, error)
            assertFalse(error.isRetryable())
        }
    }

    @Test
    fun `bangumi redirect preserves destination without offering a playback retry`() {
        val url = "https://www.bilibili.com/bangumi/play/ep123"
        val error = VideoLoadError.fromException(ContentRedirectException(url))
        assertEquals(VideoLoadError.BangumiRedirect(url), error)
        assertFalse(error.isRetryable())
    }
}
