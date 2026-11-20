package com.android.purebilibili.core.network.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HdSignedHomeFeedPolicyTest {
    @Test
    fun matchesOnlyHdSignedPhoneFeed() {
        val host = "app.bilibili.com"
        val path = "/x/v2/feed/index"
        assertTrue(isHdSignedHomeFeedRequest(host, path, "android_i"))
        assertFalse(isHdSignedHomeFeedRequest(host, path, "android"))
        assertFalse(isHdSignedHomeFeedRequest(host, path, "android_hd"))
        assertFalse(isHdSignedHomeFeedRequest(host, path, null))
        assertFalse(isHdSignedHomeFeedRequest(host, "$path/story", "android_i"))
        assertFalse(isHdSignedHomeFeedRequest("api.bilibili.com", path, "android_i"))
    }
}
