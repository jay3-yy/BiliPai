package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.SpaceDynamicContent
import com.android.purebilibili.data.model.response.SpaceDynamicDraw
import com.android.purebilibili.data.model.response.SpaceDynamicDrawItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.SpaceDynamicDesc
import com.android.purebilibili.data.model.response.SpaceDynamicMajor
import com.android.purebilibili.data.model.response.SpaceDynamicModules
import com.android.purebilibili.data.model.response.SpaceDynamicArchive
import com.android.purebilibili.data.model.response.SpaceDynamicArticle
import com.android.purebilibili.data.model.response.SpaceDynamicOpus
import com.android.purebilibili.data.model.response.SpaceDynamicOpusSummary
import com.android.purebilibili.data.model.response.DynamicMajorBadge
import com.android.purebilibili.data.model.response.DynamicBasic
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.DynamicInteractionModule
import com.android.purebilibili.data.model.response.DynamicOfficialVerify
import com.android.purebilibili.data.model.response.DynamicVipInfo
import com.android.purebilibili.data.model.response.DynamicMoreModule
import com.android.purebilibili.data.model.response.DynamicThreePointItem
import com.android.purebilibili.data.model.response.DynamicThreePointParams
import com.android.purebilibili.data.model.response.SpaceDynamicCount
import com.android.purebilibili.data.model.response.SpaceDynamicStat
import com.android.purebilibili.data.model.response.SpaceDynamicAuthor
import com.android.purebilibili.feature.dynamic.DynamicCommentTarget
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentTargets
import com.android.purebilibili.feature.dynamic.components.resolveDynamicArchiveBadgeLabel
import com.android.purebilibili.feature.dynamic.components.DynamicCardMediaAction
import com.android.purebilibili.feature.dynamic.components.resolveDynamicCardMediaAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class SpaceDynamicLoadPolicyTest {

    @Test
    fun resolveSpaceDynamicCardItem_preservesOfficialPreviewMetadataAndWebContentForReposts() {
        val interaction = DynamicInteractionModule(
            items = listOf(
                DynamicInteractionItem(
                    type = 1,
                    desc = DynamicDesc(text = "官方摘要"),
                    uri = "bilibili://comment/detail/100",
                    extend_click_param = mapOf("comment_root_id" to "100")
                ),
                DynamicInteractionItem(
                    type = 0,
                    desc = DynamicDesc(text = "UP主点赞")
                )
            )
        )
        val originalInteraction = interaction.copy(
            items = listOf(
                interaction.items.first().copy(
                    uri = "bilibili://comment/detail/200",
                    extend_click_param = mapOf("comment_root_id" to "200")
                )
            )
        )
        val original = SpaceDynamicItem(
            id_str = "original",
            modules = SpaceDynamicModules(
                module_interaction = originalInteraction,
                module_dynamic = SpaceDynamicContent(
                    desc = SpaceDynamicDesc(text = "原动态正文"),
                    major = SpaceDynamicMajor(
                        draw = SpaceDynamicDraw(
                            items = listOf(SpaceDynamicDrawItem(src = "https://example.com/original.jpg"))
                        )
                    )
                )
            )
        )
        val item = SpaceDynamicItem(
            id_str = "forward",
            orig = original,
            modules = SpaceDynamicModules(
                module_interaction = interaction,
                module_dynamic = SpaceDynamicContent(desc = SpaceDynamicDesc(text = "转发正文"))
            )
        )

        val card = resolveSpaceDynamicCardItem(item)

        assertEquals("forward", card.id_str)
        assertEquals("转发正文", card.modules.module_dynamic?.desc?.text)
        assertSame(interaction, card.modules.module_interaction)
        assertEquals("original", card.orig?.id_str)
        assertEquals("原动态正文", card.orig?.modules?.module_dynamic?.desc?.text)
        assertEquals(
            "https://example.com/original.jpg",
            card.orig?.modules?.module_dynamic?.major?.draw?.items?.single()?.src
        )
        assertSame(originalInteraction, card.orig?.modules?.module_interaction)
        assertEquals(
            mapOf("comment_root_id" to "200"),
            card.orig?.modules?.module_interaction?.items?.single()?.extend_click_param
        )
    }

    @Test
    fun resolveSpaceDynamicCardItem_preservesAuthorAvatarMarks() {
        val item = SpaceDynamicItem(
            modules = SpaceDynamicModules(
                module_author = SpaceDynamicAuthor(
                    name = "测试UP主",
                    official_verify = DynamicOfficialVerify(type = 0),
                    vip = DynamicVipInfo(type = 2, status = 1),
                )
            )
        )

        val author = resolveSpaceDynamicCardItem(item).modules.module_author
        assertEquals(0, author?.official_verify?.type)
        assertEquals(1, author?.vip?.status)
        assertEquals(2, author?.vip?.type)
    }

    @Test
    fun filterSpaceDynamicItemsByQuery_matchesDynamicTextAndArchiveTitle() {
        val items = listOf(
            SpaceDynamicItem(
                id_str = "1",
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        desc = SpaceDynamicDesc(text = "今天打游戏")
                    )
                )
            ),
            SpaceDynamicItem(
                id_str = "2",
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        major = SpaceDynamicMajor(
                            archive = SpaceDynamicArchive(
                                bvid = "BV1",
                                title = "火影周年庆攻略",
                                desc = "视频简介"
                            )
                        )
                    )
                )
            )
        )

        assertEquals(listOf("1"), filterSpaceDynamicItemsByQuery(items, "游戏").map { it.id_str })
        assertEquals(listOf("2"), filterSpaceDynamicItemsByQuery(items, "火影").map { it.id_str })
    }

    @Test
    fun filterSpaceDynamicItemsByQuery_matchesOpusTitleAndSummary() {
        val items = listOf(
            SpaceDynamicItem(
                id_str = "3",
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        major = SpaceDynamicMajor(
                            opus = SpaceDynamicOpus(
                                title = "旅行相册",
                                summary = SpaceDynamicOpusSummary(text = "记录春天的海边")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(listOf("3"), filterSpaceDynamicItemsByQuery(items, "旅行").map { it.id_str })
        assertEquals(listOf("3"), filterSpaceDynamicItemsByQuery(items, "海边").map { it.id_str })
    }

    @Test
    fun shouldPrefetchMoreSpaceDynamicsForSearch_onlyWhenNoLocalMatches() {
        assertEquals(
            true,
            shouldPrefetchMoreSpaceDynamicsForSearch(
                query = "星轨",
                matchCount = 0,
                hasMore = true,
                pagesFetchedForSearch = 0
            )
        )
        assertEquals(
            false,
            shouldPrefetchMoreSpaceDynamicsForSearch(
                query = "星轨",
                matchCount = 1,
                hasMore = true,
                pagesFetchedForSearch = 0
            )
        )
        assertEquals(
            false,
            shouldPrefetchMoreSpaceDynamicsForSearch(
                query = "星轨",
                matchCount = 0,
                hasMore = false,
                pagesFetchedForSearch = 0
            )
        )
        assertEquals(
            false,
            shouldPrefetchMoreSpaceDynamicsForSearch(
                query = "星轨",
                matchCount = 0,
                hasMore = true,
                pagesFetchedForSearch = SPACE_DYNAMIC_SEARCH_PREFETCH_PAGE_LIMIT
            )
        )
        assertEquals(
            false,
            shouldPrefetchMoreSpaceDynamicsForSearch(
                query = "   ",
                matchCount = 0,
                hasMore = true,
                pagesFetchedForSearch = 0
            )
        )
    }

    @Test
    fun mergeSpaceDynamicPages_appendsUniqueById() {
        val existing = listOf(
            SpaceDynamicItem(id_str = "1"),
            SpaceDynamicItem(id_str = "2")
        )
        val incoming = listOf(
            SpaceDynamicItem(id_str = "2"),
            SpaceDynamicItem(id_str = "3")
        )
        assertEquals(
            listOf("1", "2", "3"),
            mergeSpaceDynamicPages(existing, incoming).map { it.id_str }
        )
    }

    @Test
    fun filterSpaceDynamicItemsByQuery_matchesContinuousPhraseAcrossRichTextNodes() {
        // UI joins rich nodes without separators; search must match the continuous phrase.
        val items = listOf(
            SpaceDynamicItem(
                id_str = "phrase",
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        desc = SpaceDynamicDesc(
                            text = "",
                            rich_text_nodes = listOf(
                                com.android.purebilibili.data.model.response.SpaceDynamicRichText(
                                    type = "RICH_TEXT_NODE_TYPE_TEXT",
                                    text = "今天天气"
                                ),
                                com.android.purebilibili.data.model.response.SpaceDynamicRichText(
                                    type = "RICH_TEXT_NODE_TYPE_TEXT",
                                    text = "真不错啊"
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf("phrase"),
            filterSpaceDynamicItemsByQuery(items, "天气真不错").map { it.id_str }
        )
        assertEquals(
            listOf("phrase"),
            filterSpaceDynamicItemsByQuery(items, "今天天气真不错啊").map { it.id_str }
        )
    }

    @Test
    fun filterSpaceDynamicItemsByQuery_matchesOpusSummaryRichNodesWithoutPlainText() {
        val items = listOf(
            SpaceDynamicItem(
                id_str = "opus-only",
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        major = SpaceDynamicMajor(
                            type = "MAJOR_TYPE_OPUS",
                            opus = SpaceDynamicOpus(
                                title = "",
                                summary = SpaceDynamicOpusSummary(
                                    text = "",
                                    rich_text_nodes = listOf(
                                        com.android.purebilibili.data.model.response.SpaceDynamicRichText(
                                            type = "RICH_TEXT_NODE_TYPE_TEXT",
                                            text = "深夜加班写代码"
                                        ),
                                        com.android.purebilibili.data.model.response.SpaceDynamicRichText(
                                            type = "RICH_TEXT_NODE_TYPE_TEXT",
                                            text = "只为修一个搜索bug"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf("opus-only"),
            filterSpaceDynamicItemsByQuery(items, "加班写代码只为修").map { it.id_str }
        )
        assertEquals(
            listOf("opus-only"),
            filterSpaceDynamicItemsByQuery(items, "搜索bug").map { it.id_str }
        )
    }

    @Test
    fun filterSpaceDynamicItemsByQuery_matchesRichTextNodesAndRepostOrig() {
        val items = listOf(
            SpaceDynamicItem(
                id_str = "rich",
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        desc = SpaceDynamicDesc(
                            text = "",
                            rich_text_nodes = listOf(
                                com.android.purebilibili.data.model.response.SpaceDynamicRichText(
                                    type = "RICH_TEXT_NODE_TYPE_TEXT",
                                    text = "只有富文本节点有关键词夜航船"
                                )
                            )
                        )
                    )
                )
            ),
            SpaceDynamicItem(
                id_str = "repost",
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        desc = SpaceDynamicDesc(text = "转发了一条动态")
                    )
                ),
                orig = SpaceDynamicItem(
                    id_str = "orig",
                    modules = SpaceDynamicModules(
                        module_dynamic = SpaceDynamicContent(
                            major = SpaceDynamicMajor(
                                archive = SpaceDynamicArchive(
                                    bvid = "BV9",
                                    title = "被转发的原视频：星轨摄影"
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf("rich"),
            filterSpaceDynamicItemsByQuery(items, "夜航船").map { it.id_str }
        )
        assertEquals(
            listOf("repost"),
            filterSpaceDynamicItemsByQuery(items, "星轨").map { it.id_str }
        )
        assertEquals(
            listOf("repost"),
            filterSpaceDynamicItemsByQuery(items, "BV9").map { it.id_str }
        )
    }

    @Test
    fun toDynamicRichTextNode_prefersOrigTextAndKeepsJumpUrlForAt() {
        // Access via mapping a forward body that only has orig_text + jump_url on AT nodes.
        val item = SpaceDynamicItem(
            id_str = "at-map",
            modules = SpaceDynamicModules(
                module_dynamic = SpaceDynamicContent(
                    desc = SpaceDynamicDesc(
                        rich_text_nodes = listOf(
                            com.android.purebilibili.data.model.response.SpaceDynamicRichText(
                                type = "RICH_TEXT_NODE_TYPE_AT",
                                text = "",
                                orig_text = "@奇妙的摸鱼禁止",
                                jump_url = "//space.bilibili.com/99",
                                rid = "99"
                            )
                        )
                    )
                )
            )
        )
        val node = resolveSpaceDynamicCardItem(item)
            .modules.module_dynamic?.desc?.rich_text_nodes?.single()
        assertEquals("@奇妙的摸鱼禁止", node?.text)
        assertEquals("99", node?.rid)
        assertEquals("//space.bilibili.com/99", node?.jump_url)
    }

    @Test
    fun resolveSpaceDynamicCardItem_mapsForwardOrigForNestedDisplay() {
        val item = SpaceDynamicItem(
            id_str = "forward",
            type = "DYNAMIC_TYPE_FORWARD",
            modules = SpaceDynamicModules(
                module_dynamic = SpaceDynamicContent(
                    desc = SpaceDynamicDesc(text = "我的4P也到了 查看图片")
                )
            ),
            orig = SpaceDynamicItem(
                id_str = "orig",
                type = "DYNAMIC_TYPE_DRAW",
                modules = SpaceDynamicModules(
                    module_author = com.android.purebilibili.data.model.response.SpaceDynamicAuthor(
                        mid = 1L,
                        name = "奇妙的摸鱼禁止",
                        face = "https://face"
                    ),
                    module_dynamic = SpaceDynamicContent(
                        desc = SpaceDynamicDesc(text = "大疆Pocket收纳包"),
                        major = SpaceDynamicMajor(
                            type = "MAJOR_TYPE_DRAW",
                            draw = SpaceDynamicDraw(
                                items = listOf(
                                    SpaceDynamicDrawItem(
                                        src = "https://i0.hdslb.com/bfs/new_dyn/bag.jpg",
                                        width = 800,
                                        height = 800
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val mapped = resolveSpaceDynamicCardItem(item)
        assertEquals("DYNAMIC_TYPE_FORWARD", mapped.type)
        assertEquals("我的4P也到了 查看图片", mapped.modules.module_dynamic?.desc?.text)
        val orig = mapped.orig
        assertEquals("orig", orig?.id_str)
        assertEquals("DYNAMIC_TYPE_DRAW", orig?.type)
        assertEquals("奇妙的摸鱼禁止", orig?.modules?.module_author?.name)
        assertEquals("大疆Pocket收纳包", orig?.modules?.module_dynamic?.desc?.text)
        assertEquals(
            listOf("https://i0.hdslb.com/bfs/new_dyn/bag.jpg"),
            orig?.modules?.module_dynamic?.major?.draw?.items?.map { it.src }
        )
    }

    @Test
    fun resolveSpaceDynamicCardItem_preservesDeleteMenuParams() {
        val item = SpaceDynamicItem(
            id_str = "1063487284684259332",
            modules = SpaceDynamicModules(
                module_more = DynamicMoreModule(
                    three_point_items = listOf(
                        DynamicThreePointItem(
                            label = "删除",
                            type = "THREE_POINT_DELETE",
                            params = DynamicThreePointParams(
                                dyn_id_str = "1063487284684259332",
                                dyn_type = 1,
                                rid_str = "1063487284684259332"
                            )
                        )
                    )
                )
            )
        )

        val dynamic = resolveSpaceDynamicCardItem(item)
        val params = dynamic.modules.module_more?.three_point_items?.single()?.params

        assertEquals("1063487284684259332", params?.dyn_id_str)
        assertEquals(1, params?.dyn_type)
        assertEquals("1063487284684259332", params?.rid_str)
    }

    @Test
    fun shouldRequestInitialSpaceDynamicLoad_onlyTriggersBeforeFirstCompletedLoad() {
        assertEquals(
            true,
            shouldRequestInitialSpaceDynamicLoad(
                hasLoadedOnce = false,
                isLoading = false
            )
        )
        assertEquals(
            false,
            shouldRequestInitialSpaceDynamicLoad(
                hasLoadedOnce = false,
                isLoading = true
            )
        )
        assertEquals(
            false,
            shouldRequestInitialSpaceDynamicLoad(
                hasLoadedOnce = true,
                isLoading = false
            )
        )
    }

    @Test
    fun resolveSpaceDynamicPresentationState_treatsUntouchedEmptyStateAsLoading() {
        assertEquals(
            SpaceDynamicPresentationState.LOADING,
            resolveSpaceDynamicPresentationState(
                itemCount = 0,
                isLoading = false,
                hasLoadedOnce = false,
                lastLoadFailed = false
            )
        )
    }

    @Test
    fun resolveSpaceDynamicPresentationState_returnsEmptyAfterSuccessfulEmptyLoad() {
        assertEquals(
            SpaceDynamicPresentationState.EMPTY,
            resolveSpaceDynamicPresentationState(
                itemCount = 0,
                isLoading = false,
                hasLoadedOnce = true,
                lastLoadFailed = false
            )
        )
    }

    @Test
    fun resolveSpaceDynamicPresentationState_returnsErrorAfterFailedEmptyLoad() {
        assertEquals(
            SpaceDynamicPresentationState.ERROR,
            resolveSpaceDynamicPresentationState(
                itemCount = 0,
                isLoading = false,
                hasLoadedOnce = true,
                lastLoadFailed = true
            )
        )
    }

    @Test
    fun resolveSpaceDynamicPresentationState_prefersContentWhenItemsExist() {
        assertEquals(
            SpaceDynamicPresentationState.CONTENT,
            resolveSpaceDynamicPresentationState(
                itemCount = 3,
                isLoading = false,
                hasLoadedOnce = true,
                lastLoadFailed = false
            )
        )
    }

    @Test
    fun resolveSpaceDynamicCardItems_maps_space_draw_to_shared_media_capable_card() {
        val mapped = resolveSpaceDynamicCardItems(
            listOf(
                SpaceDynamicItem(
                    id_str = "123",
                    modules = SpaceDynamicModules(
                        module_dynamic = SpaceDynamicContent(
                            major = SpaceDynamicMajor(
                                draw = SpaceDynamicDraw(
                                    id = 9L,
                                    items = listOf(
                                        SpaceDynamicDrawItem(src = "a", width = 10, height = 10),
                                        SpaceDynamicDrawItem(src = "b", width = 20, height = 20)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).first()

        val mediaAction = assertIs<DynamicCardMediaAction.PreviewImages>(
            resolveDynamicCardMediaAction(mapped, clickedIndex = 1)
        )

        assertEquals(listOf("a", "b"), mediaAction.images)
        assertEquals(1, mediaAction.initialIndex)
    }

    @Test
    fun resolveSpaceDynamicCardItems_maps_space_article_to_shared_article_card() {
        val mapped = resolveSpaceDynamicCardItems(
            listOf(
                SpaceDynamicItem(
                    id_str = "1200069469486972932",
                    type = "DYNAMIC_TYPE_ARTICLE",
                    basic = DynamicBasic(
                        comment_id_str = "1200069469486972932",
                        comment_type = 17,
                        rid_str = "1200069469486972932"
                    ),
                    modules = SpaceDynamicModules(
                        module_dynamic = SpaceDynamicContent(
                            major = SpaceDynamicMajor(
                                type = "MAJOR_TYPE_ARTICLE",
                                article = SpaceDynamicArticle(
                                    id = 1200069469486972932L,
                                    title = "长图文标题",
                                    desc = "完整长图文摘要",
                                    covers = listOf("https://i0.hdslb.com/bfs/article/cover.jpg"),
                                    jump_url = "https://www.bilibili.com/opus/1200069469486972932"
                                )
                            )
                        ),
                        module_stat = SpaceDynamicStat(
                            comment = SpaceDynamicCount(count = 5),
                            forward = SpaceDynamicCount(count = 2),
                            like = SpaceDynamicCount(count = 32)
                        )
                    )
                )
            )
        ).first()

        val article = mapped.modules.module_dynamic?.major?.article
        val desc = mapped.modules.module_dynamic?.desc
        val stat = mapped.modules.module_stat
        val mediaAction = assertIs<DynamicCardMediaAction.PreviewImages>(
            resolveDynamicCardMediaAction(mapped, clickedIndex = 0)
        )

        assertEquals(
            listOf(DynamicCommentTarget(oid = 1200069469486972932L, type = 17)),
            resolveDynamicCommentTargets(mapped)
        )
        assertEquals("长图文标题\n完整长图文摘要", desc?.text)
        assertEquals(5, stat?.comment?.count)
        assertEquals(2, stat?.forward?.count)
        assertEquals(32, stat?.like?.count)
        assertEquals(1200069469486972932L, article?.id)
        assertEquals("长图文标题", article?.title)
        assertEquals("完整长图文摘要", article?.desc)
        assertEquals(listOf("https://i0.hdslb.com/bfs/article/cover.jpg"), mediaAction.images)
    }

    @Test
    fun resolveSpaceDynamicCardItems_preserves_space_archive_charge_badge() {
        val mapped = resolveSpaceDynamicCardItems(
            listOf(
                SpaceDynamicItem(
                    id_str = "1200000000000000000",
                    type = "DYNAMIC_TYPE_AV",
                    modules = SpaceDynamicModules(
                        module_dynamic = SpaceDynamicContent(
                            major = SpaceDynamicMajor(
                                type = "MAJOR_TYPE_ARCHIVE",
                                archive = SpaceDynamicArchive(
                                    aid = "123",
                                    bvid = "BV1xx411c7mD",
                                    title = "空间充电动态",
                                    badge = DynamicMajorBadge(text = "充电专属"),
                                    isChargingArc = true,
                                    elecArcType = 1
                                )
                            )
                        )
                    )
                )
            )
        ).first()

        val archive = mapped.modules.module_dynamic?.major?.archive

        assertEquals("充电专属", archive?.badge?.text)
        assertEquals(true, archive?.isChargingArc)
        assertEquals(1, archive?.elecArcType)
        assertEquals("充电专属", archive?.let(::resolveDynamicArchiveBadgeLabel))
    }
}
