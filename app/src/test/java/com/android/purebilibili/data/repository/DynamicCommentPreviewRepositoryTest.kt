package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.AppDynamicCommentPreview
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicBasic
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.DynamicInteractionModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.feature.dynamic.components.resolveDynamicCommentPreviewTarget
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentTarget
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DynamicCommentPreviewRepositoryTest {
    private val like = DynamicInteractionItem(type = 2, desc = DynamicDesc(text = "UP主觉得很赞"))
    private fun comment(text: String = "无ID的旧Web摘要", root: Long = 0L) = DynamicInteractionItem(
        type = 1,
        desc = DynamicDesc(text = text),
        extend_click_param = if (root > 0L) mapOf("comment_root_id" to root.toString()) else emptyMap(),
    )
    private fun card(id: Long = 100L, author: Long = 9L, comments: List<DynamicInteractionItem> = listOf(comment())) = DynamicItem(
        id_str = id.toString(),
        type = "DYNAMIC_TYPE_DRAW",
        basic = DynamicBasic(comment_id_str = "888", comment_type = 11),
        modules = DynamicModules(
            module_author = DynamicAuthorModule(mid = author),
            module_dynamic = DynamicContentModule(desc = DynamicDesc(text = "不能改写的动态正文")),
            module_interaction = DynamicInteractionModule(comments + like),
        ),
    )
    private fun official(id: Long, root: Long, text: String = "官方指定摘要") = AppDynamicCommentPreview(
        dynamicId = id.toString(),
        commentOid = 888L,
        commentType = 11,
        interaction = DynamicInteractionModule(listOf(comment(text, root))),
    )
    private fun root(item: DynamicItem): Long {
        val preview = item.modules.module_interaction!!.items.first { it.type == 1 }
        return resolveDynamicCommentPreviewTarget(item, preview)!!.commentRootRpid
    }

    @Test
    fun replacesTheWholeOfficialSummaryRatherThanAttachingItsIdToWebText() = runTest {
        val input = card()
        val output = enrichDynamicCommentPreviews(listOf(input)) { _, _ -> mapOf("100" to official(100, 701, "另一条官方摘要")) }.single()
        assertEquals("另一条官方摘要", output.modules.module_interaction!!.items.first().desc!!.text)
        assertEquals(701L, root(output))
        assertSame(input.modules.module_dynamic, output.modules.module_dynamic)
        assertSame(like, output.modules.module_interaction!!.items.last())
        assertEquals("无ID的旧Web摘要", input.modules.module_interaction!!.items.first().desc!!.text)
    }

    @Test
    fun aPageBatchesSameAuthorCardsButKeepsTheirOriginalOrderAndSeparateFloors() = runTest {
        val queries = mutableListOf<Pair<Long, String>>()
        val output = enrichDynamicCommentPreviews(listOf(card(90), card(100))) { host, cursor ->
            queries += host to cursor
            mapOf("100" to official(100, 701), "90" to official(90, 702))
        }
        assertEquals(listOf("90", "100"), output.map { it.id_str })
        assertEquals(listOf(702L, 701L), output.map(::root))
        assertEquals(listOf(9L to "101"), queries)
    }

    @Test
    fun seeksAnOlderRequestedCardDirectlyInsteadOfScanningEveryHistoryPage() = runTest {
        val queries = mutableListOf<String>()
        val output = enrichDynamicCommentPreviews(listOf(card(100), card(50))) { _, cursor ->
            queries += cursor
            if (cursor == "101") mapOf("100" to official(100, 701))
            else mapOf("50" to official(50, 702))
        }
        assertEquals(listOf(701L, 702L), output.map(::root))
        assertEquals(listOf("101", "51"), queries)
    }

    @Test
    fun wrongDynamicIdentityCannotDonateFloorIdsAndMissingCardsDoNotLoop() = runTest {
        var calls = 0
        val output = enrichDynamicCommentPreviews(listOf(card())) { _, _ ->
            calls++
            mapOf("100" to official(999, 701), "1" to official(1, 702))
        }.single()
        assertEquals(listOf(like), output.modules.module_interaction!!.items)
        assertEquals("不能改写的动态正文", output.modules.module_dynamic!!.desc!!.text)
        assertEquals(1, calls)
    }

    @Test
    fun authoritativeNoSummaryDoesNotKeepTheOldUnlocatedWebComment() = runTest {
        val output = enrichDynamicCommentPreviews(listOf(card())) { _, _ ->
            mapOf("100" to AppDynamicCommentPreview("100", 888, 11, null))
        }.single()
        assertEquals(listOf(like), output.modules.module_interaction!!.items)
    }

    @Test
    fun failureRetainsKnownLocationsAndContentWithoutKeepingUnknownOrInventedFloors() = runTest {
        val input = card(comments = listOf(comment("已定位的摘要", 701), comment()))
        val output = enrichDynamicCommentPreviews(listOf(input)) { _, _ -> throw IOException("offline") }.single()
        assertEquals(listOf("已定位的摘要", "UP主觉得很赞"), output.modules.module_interaction!!.items.map { it.desc!!.text })
        assertEquals(701L, root(output))
        assertSame(input.modules.module_dynamic, output.modules.module_dynamic)
    }

    @Test
    fun cancellationDoesNotPublishPartialEnrichment() = runTest {
        assertFailsWith<CancellationException> {
            enrichDynamicCommentPreviews(listOf(card())) { _, _ -> throw CancellationException("switched account") }
        }
    }

    @Test
    fun enrichedServerSubjectOverridesADifferentWebSubjectForTheSameDynamic() = runTest {
        val input = card().copy(basic = DynamicBasic(comment_id_str = "100", comment_type = 17))
        val output = enrichDynamicCommentPreviews(listOf(input)) { _, _ -> mapOf("100" to official(100, 701)) }.single()
        assertEquals(888L, resolveDynamicCommentTarget(output)!!.oid)
        assertEquals(11, resolveDynamicCommentTarget(output)!!.type)
        assertEquals(701L, root(output))
    }

    @Test
    fun nestedOriginalHasItsOwnAuthorLookupAndDoesNotBorrowTheRepostsFloor() = runTest {
        val repost = card(100, 9).copy(orig = card(90, 8))
        val output = enrichDynamicCommentPreviews(listOf(repost)) { author, _ ->
            if (author == 9L) mapOf("100" to official(100, 701)) else mapOf("90" to official(90, 702))
        }.single()
        assertEquals(701L, root(output))
        assertEquals(702L, root(output.orig!!))
    }

    @Test
    fun cachedClickParamsSurviveSerializationWhileLegacyUnlocatedOriginalsAreRemoved() {
        val input = listOf(card(comments = listOf(comment("已定位", 318843506177))).copy(orig = card(90)))
        val json = Json { ignoreUnknownKeys = true }
        val restored = retainLocatedDynamicCommentPreviews(json.decodeFromString<List<DynamicItem>>(json.encodeToString(input)))
        assertEquals(318843506177L, root(restored.single()))
        assertEquals(listOf(like), restored.single().orig!!.modules.module_interaction!!.items)
        assertEquals("不能改写的动态正文", restored.single().orig!!.modules.module_dynamic!!.desc!!.text)
    }

    @Test
    fun alreadyLocatedCardsReuseTheSnapshotWithoutARequest() = runTest {
        val input = listOf(card(comments = listOf(comment("已定位", 701))))
        val output = enrichDynamicCommentPreviews(input) { _, _ -> error("Located snapshots need no public lookup") }
        assertSame(input, output)
        assertEquals(701L, root(output.single()))
    }

    @Test
    fun detailRetainsTheLocatedSeedAsAWholeWithoutLosingItsOwnHintsOrContent() = runTest {
        val seed = card(comments = listOf(comment("原摘要", 701)))
        val detailHint = like.copy(desc = DynamicDesc(text = "详情点赞提示"))
        val detail = card().copy(
            basic = DynamicBasic(comment_id_str = "100", comment_type = 17),
            modules = card().modules.copy(module_interaction = DynamicInteractionModule(listOf(comment("详情无ID摘要"), detailHint))),
        )
        val merged = restoreLocatedDynamicCommentPreviewMetadata(detail, seed)
        val output = enrichDynamicCommentPreviews(listOf(merged)) { _, _ -> error("The seed already owns a valid floor") }.single()
        assertEquals(listOf("原摘要", "详情点赞提示"), output.modules.module_interaction!!.items.map { it.desc!!.text })
        assertEquals(701L, root(output))
        assertEquals(11, resolveDynamicCommentTarget(output)!!.type)
        assertEquals(888L, resolveDynamicCommentTarget(output)!!.oid)
        assertSame(detail.modules.module_dynamic, output.modules.module_dynamic)
    }

    @Test
    fun concurrentAuthorLookupsAreBoundedAndKeepEachAuthorsReplyIdentity() = runTest {
        var active = 0
        var maximum = 0
        val cards = (1L..6L).map { card(it * 100, it) }
        val output = enrichDynamicCommentPreviews(cards) { author, _ ->
            active++
            maximum = maxOf(maximum, active)
            delay(10)
            active--
            mapOf((author * 100).toString() to official(author * 100, 700 + author))
        }
        assertTrue(maximum <= 4)
        assertEquals((1L..6L).map { 700 + it }, output.map(::root))
    }
}
