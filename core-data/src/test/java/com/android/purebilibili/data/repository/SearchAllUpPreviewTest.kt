package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.SearchAllResponse
import com.android.purebilibili.data.model.response.SearchUpItem
import com.android.purebilibili.data.model.response.SearchUpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SearchAllUpPreviewTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `empty featured category still previews partial nickname matches in server order`() = runTest {
        val all = json.decodeFromString<SearchAllResponse>(
            """{"code":0,"data":{"page":1,"pagesize":20,"numResults":1000,"numPages":50,
                "pageinfo":{"bili_user":{"numResults":256,"total":256,"pages":13}},
                "result":[{"result_type":"bili_user","data":[]}]}}"""
        ).data!!
        val users = json.decodeFromString<SearchUpResponse>(
            """{"code":0,"data":{"page":1,"pagesize":30,"numResults":352,"numPages":12,
                "result":[{"mid":11,"uname":"Orbit"},
                    {"mid":12,"uname":"Orbit的工作室"},
                    {"mid":3706929260006322,"uname":"夜航<em>Orbit</em>","upic":"//i0.hdslb.com/face.jpg"},
                    {"mid":14,"uname":"小Orbit"}]}}"""
        ).data!!

        val preview = resolveSearchAllUpPreview(1, all.pageinfo.getValue("bili_user").numResults, emptyList()) {
            users.result.orEmpty().map { it.cleanupFields() }
        }

        assertEquals(listOf(11L, 12L, 3706929260006322L), preview.map { it.mid })
        assertEquals("夜航Orbit", preview.last().uname)
        assertEquals("https://i0.hdslb.com/face.jpg", preview.last().upic)
    }

    @Test
    fun `featured users later pages and no user matches do not load a preview`() = runTest {
        val featured = listOf(SearchUpItem(mid = 7, uname = "精选用户"))
        assertSame(featured, resolveSearchAllUpPreview(1, 100, featured) { error("already featured") })
        assertTrue(resolveSearchAllUpPreview(2, 100, emptyList()) { error("video continuation") }.isEmpty())
        assertTrue(resolveSearchAllUpPreview(1, 0, emptyList()) { error("no user matches") }.isEmpty())
    }

    @Test
    fun `short empty and duplicate user results never fabricate preview entries`() = runTest {
        val user = SearchUpItem(mid = 7, uname = "部分Nickname")
        assertEquals(listOf(user), resolveSearchAllUpPreview(1, 10, emptyList()) { listOf(user, user) })
        assertTrue(resolveSearchAllUpPreview(1, 10, emptyList()) { emptyList() }.isEmpty())
    }

    @Test
    fun `user request failures and cancellation remain failures instead of empty results`() = runTest {
        val failure = IllegalStateException("搜索请求被拦截")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            resolveSearchAllUpPreview(1, 10, emptyList()) { throw failure }
        })
        assertFailsWith<CancellationException> {
            resolveSearchAllUpPreview(1, 10, emptyList()) { throw CancellationException("new query") }
        }
    }

    @Test
    fun `successful code carrying a risk voucher is not an empty successful search`() {
        val payload = """{"code":0,"data":{"v_voucher":"voucher_search_challenge"}}"""
        val all = json.decodeFromString<SearchAllResponse>(payload)
        val users = json.decodeFromString<SearchUpResponse>(payload)
        assertFailsWith<IllegalStateException> { requireSearchPayload(all.data != null, all.data?.v_voucher) }
        assertFailsWith<IllegalStateException> { requireSearchPayload(users.data != null, users.data?.v_voucher) }
        assertFailsWith<IllegalStateException> { requireSearchPayload(false, null) }
        requireSearchPayload(true, null)
        requireSearchPayload(true, "")
    }
}
