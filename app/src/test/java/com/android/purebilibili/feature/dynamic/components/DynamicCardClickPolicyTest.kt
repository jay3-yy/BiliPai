package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.ArticleMajor
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.DrawMajor
import com.android.purebilibili.data.model.response.DrawItem
import com.android.purebilibili.data.model.response.OpusContentBlock
import com.android.purebilibili.data.model.response.OpusLinkCard
import com.android.purebilibili.data.model.response.OpusMajor
import com.android.purebilibili.data.model.response.OpusPic
import com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout
import com.android.purebilibili.data.model.response.UgcSeasonMajor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicCardClickPolicyTest {

    @Test
    fun renderableDynamicImages_dropBlankAndDuplicateUrls() {
        val drawItems = resolveRenderableDrawItems(
            listOf(
                DrawItem(src = " "),
                DrawItem(src = "https://i0.hdslb.com/one.jpg"),
                DrawItem(src = "http://i0.hdslb.com/one.jpg"),
            )
        )
        val opusPics = resolveRenderableOpusPics(
            listOf(
                OpusPic(url = ""),
                OpusPic(url = " https://i0.hdslb.com/one.jpg "),
                OpusPic(url = "http://i0.hdslb.com/one.jpg"),
            )
        )

        assertEquals(listOf("https://i0.hdslb.com/one.jpg"), drawItems.map { it.src })
        assertEquals(listOf("https://i0.hdslb.com/one.jpg"), opusPics.map { it.url })
    }

    @Test
    fun drawGridUsesOpusPicturesAsCanonicalSourceWhenAvailable() {
        assertEquals(
            false,
            shouldRenderDynamicDrawGrid(
                hasFullOpusImageContent = false,
                opusPics = listOf(OpusPic(url = "https://i0.hdslb.com/one.jpg")),
            ),
        )
        assertEquals(
            true,
            shouldRenderDynamicDrawGrid(
                hasFullOpusImageContent = false,
                opusPics = listOf(OpusPic(url = " ")),
            ),
        )
    }

    @Test
    fun mediaPreviewUsesTheSameFilteredOpusImagesAsTheRenderedGrid() {
        val item = DynamicItem(
            id_str = "single-picture-dynamic",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        draw = DrawMajor(items = listOf(DrawItem(src = "https://i0.hdslb.com/duplicate.jpg"))),
                        opus = OpusMajor(
                            pics = listOf(
                                OpusPic(url = " "),
                                OpusPic(url = "https://i0.hdslb.com/one.jpg"),
                                OpusPic(url = "http://i0.hdslb.com/one.jpg"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val action = resolveDynamicCardMediaAction(item, clickedIndex = 0)

        assertEquals(
            DynamicCardMediaAction.PreviewImages(
                images = listOf("https://i0.hdslb.com/one.jpg"),
                initialIndex = 0,
            ),
            action,
        )
    }

    @Test
    fun resolveDynamicCardPrimaryAction_prefersVideoWhenArchiveBvidExists() {
        val item = DynamicItem(
            id_str = "123",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        archive = ArchiveMajor(bvid = "BV1xx411c7mD")
                    )
                )
            )
        )

        val action = resolveDynamicCardPrimaryAction(item)

        assertTrue(action is DynamicCardPrimaryAction.OpenVideo)
        assertEquals("BV1xx411c7mD", (action as DynamicCardPrimaryAction.OpenVideo).bvid)
    }

    @Test
    fun resolveDynamicCardPrimaryAction_opensLiveForLiveMajor() {
        val item = DynamicItem(
            id_str = "live-dyn",
            type = "DYNAMIC_TYPE_LIVE",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        live = com.android.purebilibili.data.model.response.LiveMajor(
                            id = "22759954",
                            title = "直播标题"
                        )
                    )
                ),
                module_author = com.android.purebilibili.data.model.response.DynamicAuthorModule(
                    name = "主播"
                )
            )
        )

        val action = resolveDynamicCardPrimaryAction(item)

        assertTrue(action is DynamicCardPrimaryAction.OpenLive)
        assertEquals(22759954L, (action as DynamicCardPrimaryAction.OpenLive).roomId)
        assertEquals("直播标题", action.title)
        assertEquals("主播", action.uname)
    }

    @Test
    fun resolveDynamicCardPrimaryAction_opensDynamicDetailWhenNoVideo() {
        val item = DynamicItem(id_str = "  987654321  ")

        val action = resolveDynamicCardPrimaryAction(item)

        assertTrue(action is DynamicCardPrimaryAction.OpenDynamicDetail)
        assertEquals("987654321", (action as DynamicCardPrimaryAction.OpenDynamicDetail).dynamicId)
    }


    @Test
    fun resolveDynamicCardPrimaryAction_usesLegacyArticleJumpUrlWhenPayloadIdIsMissing() {
        val item = DynamicItem(
            id_str = "dynamic-article",
            type = "DYNAMIC_TYPE_ARTICLE",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        type = "MAJOR_TYPE_ARTICLE",
                        article = ArticleMajor(
                            id = 0L,
                            title = "旧版专栏",
                            jump_url = "https://www.bilibili.com/read/cv123456",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            DynamicCardPrimaryAction.OpenArticle(123456L, "旧版专栏"),
            resolveDynamicCardPrimaryAction(item),
        )
    }

    @Test
    fun resolveDynamicCardPrimaryAction_returnsNoneWhenNoVideoAndNoId() {
        val item = DynamicItem(id_str = "  ")

        val action = resolveDynamicCardPrimaryAction(item)

        assertTrue(action is DynamicCardPrimaryAction.None)
    }

    @Test
    fun resolveDynamicCardPrimaryAction_usesArchiveJumpUrlWhenBvidMissing() {
        val item = DynamicItem(
            id_str = "123",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        archive = ArchiveMajor(
                            bvid = "",
                            jump_url = "//www.bilibili.com/video/BV1d4421Z7nW/"
                        )
                    )
                )
            )
        )

        val action = resolveDynamicCardPrimaryAction(item)

        assertTrue(action is DynamicCardPrimaryAction.OpenVideo)
        assertEquals("BV1d4421Z7nW", (action as DynamicCardPrimaryAction.OpenVideo).bvid)
    }

    @Test
    fun resolveDynamicCardPrimaryAction_usesUgcSeasonJumpUrlWhenArchiveMissing() {
        val item = DynamicItem(
            id_str = "123",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        ugc_season = UgcSeasonMajor(
                            title = "合集标题",
                            jump_url = "//www.bilibili.com/video/BV1oeWNebEv2/"
                        )
                    )
                )
            )
        )

        val action = resolveDynamicCardPrimaryAction(item)

        assertTrue(action is DynamicCardPrimaryAction.OpenVideo)
        assertEquals("BV1oeWNebEv2", (action as DynamicCardPrimaryAction.OpenVideo).bvid)
    }

    @Test
    fun resolveDynamicCardPrimaryAction_usesUgcSeasonAidWhenJumpUrlMissing() {
        val item = DynamicItem(
            id_str = "123",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        ugc_season = UgcSeasonMajor(
                            title = "合集标题",
                            aid = 1129813966L
                        )
                    )
                )
            )
        )

        val action = resolveDynamicCardPrimaryAction(item)

        assertTrue(action is DynamicCardPrimaryAction.OpenVideo)
        assertEquals("av1129813966", (action as DynamicCardPrimaryAction.OpenVideo).bvid)
    }

    @Test
    fun dispatchDynamicCardPrimaryClick_prefersOverrideOverDefaultAction() {
        val item = DynamicItem(
            id_str = "123",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        archive = ArchiveMajor(bvid = "BV1xx411c7mD")
                    )
                )
            )
        )
        val events = mutableListOf<String>()

        dispatchDynamicCardPrimaryClick(
            item = item,
            action = DynamicCardPrimaryAction.OpenVideo("BV1xx411c7mD"),
            onPrimaryClickOverride = { events += "comment:${it.id_str}" },
            onVideoClick = { events += "video:$it" },
            onBangumiClick = { seasonId, epId -> events += "bangumi:$seasonId:$epId" },
            onArticleClick = { articleId, _ -> events += "article:$articleId" },
            onDynamicDetailClick = { events += "dynamic:$it" },
            onUserClick = { events += "user:$it" },
            onLiveClick = { roomId, _, _ -> events += "live:$roomId" }
        )

        assertEquals(listOf("comment:123"), events)
    }

    @Test
    fun shouldEnableDynamicCardPrimaryClick_allowsOverrideWhenDefaultActionIsUnavailable() {
        assertTrue(
            shouldEnableDynamicCardPrimaryClick(
                action = DynamicCardPrimaryAction.None,
                hasArticleClick = false,
                hasDynamicDetailClick = false,
                hasPrimaryClickOverride = true
            )
        )
    }

    @Test
    fun resolveDynamicWatchLaterAid_usesArchiveAidWhenVideoDynamicHasAid() {
        val item = DynamicItem(
            id_str = "123",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        archive = ArchiveMajor(aid = "1129813966", bvid = "BV1xx411c7mD")
                    )
                )
            )
        )

        assertEquals(1129813966L, resolveDynamicWatchLaterAid(item))
    }

    @Test
    fun resolveDynamicWatchLaterAid_usesOriginalItemForForwardedVideoDynamic() {
        val item = DynamicItem(
            id_str = "forward",
            orig = DynamicItem(
                id_str = "origin",
                modules = DynamicModules(
                    module_dynamic = DynamicContentModule(
                        major = DynamicMajor(
                            archive = ArchiveMajor(aid = "99887766", bvid = "BV1xx411c7mD")
                        )
                    )
                )
            )
        )

        assertEquals(99887766L, resolveDynamicWatchLaterAid(item))
    }

    @Test
    fun resolveDynamicWatchLaterAid_returnsNullForNonVideoDynamic() {
        val item = DynamicItem(id_str = "123")

        assertEquals(null, resolveDynamicWatchLaterAid(item))
    }

    @Test
    fun resolveDynamicCardMediaAction_previewsArticleCovers() {
        val item = DynamicItem(
            id_str = "1199344045210468386",
            type = "DYNAMIC_TYPE_ARTICLE",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        type = "MAJOR_TYPE_ARTICLE",
                        article = ArticleMajor(
                            covers = listOf(
                                "https://i0.hdslb.com/bfs/article/a.jpg",
                                "https://i0.hdslb.com/bfs/article/b.jpg"
                            )
                        )
                    )
                )
            )
        )

        val action = resolveDynamicCardMediaAction(item, clickedIndex = 1)

        assertTrue(action is DynamicCardMediaAction.PreviewImages)
        assertEquals(
            listOf(
                "https://i0.hdslb.com/bfs/article/a.jpg",
                "https://i0.hdslb.com/bfs/article/b.jpg"
            ),
            (action as DynamicCardMediaAction.PreviewImages).images
        )
        assertEquals(1, action.initialIndex)
    }

    @Test
    fun resolveDynamicCardMediaAction_opensDynamicDetailForOpusPreviewOnListCard() {
        val item = DynamicItem(
            id_str = "1201902028962398230",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        type = "MAJOR_TYPE_OPUS",
                        opus = OpusMajor(
                            pics = listOf(OpusPic(url = "https://i0.hdslb.com/opus.jpg"))
                        )
                    )
                )
            )
        )

        val action = resolveDynamicCardMediaAction(item, clickedIndex = 0, isDetail = false)

        assertTrue(action is DynamicCardMediaAction.OpenDynamicDetail)
        assertEquals("1201902028962398230", action.dynamicId)
    }

    @Test
    fun resolveDynamicCardMediaAction_previewsOpusImagesOnDetailPage() {
        val item = DynamicItem(
            id_str = "1201902028962398230",
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        type = "MAJOR_TYPE_OPUS",
                        opus = OpusMajor(
                            pics = listOf(OpusPic(url = "https://i0.hdslb.com/opus.jpg"))
                        )
                    )
                )
            )
        )

        val action = resolveDynamicCardMediaAction(item, clickedIndex = 0, isDetail = true)

        assertTrue(action is DynamicCardMediaAction.PreviewImages)
        assertEquals(listOf("https://i0.hdslb.com/opus.jpg"), action.images)
    }

    @Test
    fun resolveArticleCoverDrawItems_filtersBlankCoversForRendering() {
        val drawItems = resolveArticleCoverDrawItems(
            ArticleMajor(
                covers = listOf(
                    " ",
                    " https://i0.hdslb.com/bfs/article/a.jpg ",
                    ""
                )
            )
        )

        assertEquals(1, drawItems.size)
        assertEquals("https://i0.hdslb.com/bfs/article/a.jpg", drawItems.first().src)
    }

    @Test
    fun resolveDynamicOpusPresentationBlocks_usesFullBlocksOnlyOnDetailPage() {
        val opus = OpusMajor(
            pics = listOf(OpusPic(url = "https://i0.hdslb.com/preview.jpg")),
            contentBlocks = listOf(
                OpusContentBlock.Text("完整正文"),
                OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/full.jpg"))
            )
        )

        assertEquals(
            opus.contentBlocks,
            resolveDynamicOpusPresentationBlocks(opus = opus, isDetail = true)
        )
        assertEquals(
            emptyList(),
            resolveDynamicOpusPresentationBlocks(opus = opus, isDetail = false)
        )
    }

    @Test
    fun resolveDynamicOpusPresentationBlocks_dropsBlankAndDuplicateImageBlocks() {
        val image = OpusPic(url = "https://i0.hdslb.com/one.jpg")
        val blocks = listOf(
            OpusContentBlock.Image(OpusPic(url = " ")),
            OpusContentBlock.Image(image),
            OpusContentBlock.Divider(image.copy(url = "http://i0.hdslb.com/one.jpg")),
        )

        assertEquals(
            listOf(
                OpusContentBlock.Image(image),
                OpusContentBlock.Divider(pic = null),
            ),
            resolveDynamicOpusPresentationBlocks(
                opus = OpusMajor(contentBlocks = blocks),
                isDetail = true,
            ),
        )
    }

    @Test
    fun resolveDynamicOpusPresentationBlocks_dropsBlankTextAndTrimsEdges() {
        val blocks = listOf(
            OpusContentBlock.Text("  "),
            OpusContentBlock.Text("\n正文\n\n"),
            OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/a.jpg")),
        )

        assertEquals(
            listOf(
                OpusContentBlock.Text("正文"),
                OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/a.jpg")),
            ),
            resolveDynamicOpusPresentationBlocks(
                opus = OpusMajor(contentBlocks = blocks),
                isDetail = true,
            ),
        )
    }

    @Test
    fun detailPreviewUsesTheSameValidImagesAsRenderedBodyBlocks() {
        val opus = OpusMajor(
            pics = listOf(
                OpusPic(url = "https://i0.hdslb.com/stale-feed-image.jpg"),
                OpusPic(url = " "),
            ),
            contentBlocks = listOf(
                OpusContentBlock.Text("正文"),
                OpusContentBlock.Image(OpusPic(url = " https://i0.hdslb.com/body-image.jpg ")),
                OpusContentBlock.Divider(OpusPic(url = "http://i0.hdslb.com/body-image.jpg")),
            ),
        )
        val blocks = resolveDynamicOpusPresentationBlocks(opus = opus, isDetail = true)

        assertEquals(
            listOf("https://i0.hdslb.com/body-image.jpg"),
            resolveDynamicOpusPreviewPics(opus, blocks).map { it.url },
        )
        assertTrue(shouldRenderDynamicOpusBlocksAsFullBody(opus, blocks))
    }

    @Test
    fun shouldRenderDynamicOpusBlocksAsFullBody_usesGridForStandalonePics() {
        val textBlock = OpusContentBlock.Text("完整正文")
        val opusWithStandalonePic = OpusMajor(
            pics = listOf(OpusPic(url = "https://i0.hdslb.com/standalone.jpg")),
            contentBlocks = listOf(textBlock),
        )

        assertEquals(
            false,
            shouldRenderDynamicOpusBlocksAsFullBody(
                opus = opusWithStandalonePic,
                presentationBlocks = opusWithStandalonePic.contentBlocks,
            ),
        )
        assertEquals(
            true,
            shouldRenderDynamicOpusBlocksAsFullBody(
                opus = opusWithStandalonePic.copy(
                    contentBlocks = listOf(
                        textBlock,
                        OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/embedded.jpg")),
                    ),
                ),
                presentationBlocks = listOf(
                    textBlock,
                    OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/embedded.jpg")),
                ),
            ),
        )
        assertEquals(
            true,
            shouldRenderDynamicOpusBlocksAsFullBody(
                opus = OpusMajor(contentBlocks = listOf(textBlock)),
                presentationBlocks = listOf(textBlock),
            ),
        )
    }

    @Test
    fun resolveDynamicOpusPreviewImageLimit_removesNineImageLimitOnDetailPage() {
        assertEquals(null, resolveDynamicOpusPreviewImageLimit(isDetail = true))
        assertEquals(
            DYNAMIC_FEED_PREVIEW_MAX_IMAGES,
            resolveDynamicOpusPreviewImageLimit(isDetail = false)
        )
    }

    @Test
    fun resolveDynamicOpusLinkCardAction_routesBilibiliTargetsInApp() {
        assertEquals(
            DynamicOpusLinkCardAction.OpenVideo("BV1xx411c7mD"),
            resolveDynamicOpusLinkCardAction(
                OpusLinkCard(
                    type = "LINK_CARD_TYPE_UGC",
                    title = "视频",
                    jumpUrl = "https://www.bilibili.com/video/BV1xx411c7mD"
                )
            )
        )
        assertEquals(
            DynamicOpusLinkCardAction.OpenDynamicDetail("1201902028962398230"),
            resolveDynamicOpusLinkCardAction(
                OpusLinkCard(
                    type = "LINK_CARD_TYPE_OPUS",
                    title = "图文",
                    jumpUrl = "https://www.bilibili.com/opus/1201902028962398230"
                )
            )
        )
        assertEquals(
            DynamicOpusLinkCardAction.OpenArticle(123456L, "专栏"),
            resolveDynamicOpusLinkCardAction(
                OpusLinkCard(
                    type = "LINK_CARD_TYPE_COMMON",
                    title = "专栏",
                    jumpUrl = "https://www.bilibili.com/read/cv123456"
                )
            )
        )
        assertEquals(
            DynamicOpusLinkCardAction.OpenLive(6L),
            resolveDynamicOpusLinkCardAction(
                OpusLinkCard(
                    type = "LINK_CARD_TYPE_LIVE",
                    title = "直播",
                    jumpUrl = "https://live.bilibili.com/6"
                )
            )
        )
    }

    @Test
    fun resolveDynamicOpusLinkCardAction_routesExternalUrlAndIgnoresMissingUrl() {
        assertEquals(
            DynamicOpusLinkCardAction.OpenExternalUrl("https://uland.taobao.com/item"),
            resolveDynamicOpusLinkCardAction(
                OpusLinkCard(
                    type = "LINK_CARD_TYPE_GOODS",
                    title = "商品",
                    jumpUrl = " https://uland.taobao.com/item "
                )
            )
        )
        assertEquals(
            DynamicOpusLinkCardAction.None,
            resolveDynamicOpusLinkCardAction(
                OpusLinkCard(
                    type = "LINK_CARD_TYPE_ITEM_NULL",
                    title = "内容已失效"
                )
            )
        )
    }

    @Test
    fun resolveDynamicHeadlineTitle_prefersOpusTitle() {
        val opus = OpusMajor(title = "Opus专栏文章标题")
        val article = ArticleMajor(title = "旧专栏标题")

        assertEquals("Opus专栏文章标题", resolveDynamicHeadlineTitle(opus, article))
    }

    @Test
    fun resolveDynamicHeadlineTitle_fallsBackToArticleTitle() {
        val article = ArticleMajor(title = "文章专栏标题")

        assertEquals("文章专栏标题", resolveDynamicHeadlineTitle(null, article))
    }

    @Test
    fun resolveDynamicHeadlineTitle_returnsNullWhenBlankOrMissing() {
        assertEquals(null, resolveDynamicHeadlineTitle(OpusMajor(title = "  "), ArticleMajor(title = "")))
        assertEquals(null, resolveDynamicHeadlineTitle(null, null))
    }

    @Test
    fun resolveDynamicAuthorClickMid_returnsNullForUgcSeasonWithoutOwnerMid() {
        val item = DynamicItem(
            type = "DYNAMIC_TYPE_UGC_SEASON",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 123456L, name = "装机猿PC问答3"),
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        type = "MAJOR_TYPE_UGC_SEASON",
                        ugc_season = UgcSeasonMajor(id = 123456L, title = "装机猿PC问答3", mid = 0L)
                    )
                )
            )
        )

        val targetMid = resolveDynamicAuthorClickMid(item)

        assertNull(targetMid)
    }

    @Test
    fun resolveDynamicAuthorClickMid_returnsOwnerMidWhenUgcSeasonSuppliesMid() {
        val item = DynamicItem(
            type = "DYNAMIC_TYPE_UGC_SEASON",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 9999L, name = "装机猿PC问答3"),
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        type = "MAJOR_TYPE_UGC_SEASON",
                        ugc_season = UgcSeasonMajor(id = 9999L, title = "装机猿PC问答3", mid = 260882L)
                    )
                )
            )
        )

        val targetMid = resolveDynamicAuthorClickMid(item)

        assertEquals(260882L, targetMid)
    }

    @Test
    fun resolveDynamicAuthorClickMid_returnsNullForPgcDynamic() {
        val item = DynamicItem(
            type = "DYNAMIC_TYPE_PGC",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 111L, name = "哔哩哔哩番剧")
            )
        )

        assertNull(resolveDynamicAuthorClickMid(item))
    }

    @Test
    fun shouldExpandDynamicOpusDetailImages_onlyForExpandedLayout() {
        assertTrue(shouldExpandDynamicOpusDetailImages(DynamicDetailImageLayout.EXPANDED))
        assertFalse(shouldExpandDynamicOpusDetailImages(DynamicDetailImageLayout.THUMBNAIL))
    }

    @Test
    fun shouldExpandDynamicOpusFallbackImages_onlyOnDetailExpandedLayout() {
        assertTrue(
            shouldExpandDynamicOpusFallbackImages(
                isDetail = true,
                imageLayout = DynamicDetailImageLayout.EXPANDED,
            )
        )
        assertFalse(
            shouldExpandDynamicOpusFallbackImages(
                isDetail = true,
                imageLayout = DynamicDetailImageLayout.THUMBNAIL,
            )
        )
        assertFalse(
            shouldExpandDynamicOpusFallbackImages(
                isDetail = false,
                imageLayout = DynamicDetailImageLayout.EXPANDED,
            )
        )
    }

    @Test
    fun toggleDynamicDetailImageLayout_flipsBetweenExpandedAndThumbnail() {
        assertEquals(
            DynamicDetailImageLayout.THUMBNAIL,
            toggleDynamicDetailImageLayout(DynamicDetailImageLayout.EXPANDED)
        )
        assertEquals(
            DynamicDetailImageLayout.EXPANDED,
            toggleDynamicDetailImageLayout(DynamicDetailImageLayout.THUMBNAIL)
        )
    }

    @Test
    fun shouldEmitOpusThumbnailGridAtBlock_atFirstImageOnly() {
        val imageBlock = OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/a.jpg"))
        val textBlock = OpusContentBlock.Text("正文")
        val linkCard = OpusContentBlock.LinkCard(
            OpusLinkCard(title = "横幅", jumpUrl = "https://www.bilibili.com/")
        )

        assertTrue(
            shouldEmitOpusThumbnailGridAtBlock(
                block = imageBlock,
                thumbnailGridEmitted = false,
                hasThumbnailItems = true,
                expandImages = false,
            )
        )
        assertFalse(
            shouldEmitOpusThumbnailGridAtBlock(
                block = imageBlock,
                thumbnailGridEmitted = true,
                hasThumbnailItems = true,
                expandImages = false,
            )
        )
        assertFalse(
            shouldEmitOpusThumbnailGridAtBlock(
                block = linkCard,
                thumbnailGridEmitted = false,
                hasThumbnailItems = true,
                expandImages = false,
            )
        )
        assertFalse(
            shouldEmitOpusThumbnailGridAtBlock(
                block = textBlock,
                thumbnailGridEmitted = false,
                hasThumbnailItems = true,
                expandImages = false,
            )
        )
        assertFalse(
            shouldEmitOpusThumbnailGridAtBlock(
                block = imageBlock,
                thumbnailGridEmitted = false,
                hasThumbnailItems = true,
                expandImages = true,
            )
        )
        assertFalse(
            shouldEmitOpusThumbnailGridAtBlock(
                block = imageBlock,
                thumbnailGridEmitted = false,
                hasThumbnailItems = false,
                expandImages = false,
            )
        )
    }

    @Test
    fun resolveOpusThumbnailDrawItems_collectsImagesAndDividerPics() {
        val items = resolveOpusThumbnailDrawItems(
            listOf(
                OpusContentBlock.Text("正文"),
                OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/a.jpg", width = 1, height = 2)),
                OpusContentBlock.Divider(pic = OpusPic(url = "https://i0.hdslb.com/b.jpg")),
                OpusContentBlock.Divider(pic = null),
                OpusContentBlock.Heading("标题"),
            )
        )

        assertEquals(
            listOf(
                DrawItem(src = "https://i0.hdslb.com/a.jpg", width = 1, height = 2),
                DrawItem(src = "https://i0.hdslb.com/b.jpg"),
            ),
            items
        )
    }

    @Test
    fun shouldShowDynamicDetailImageLayoutToggle_whenMediaExists() {
        val withDraw = DynamicItem(
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        draw = DrawMajor(items = listOf(DrawItem(src = "https://i0.hdslb.com/a.jpg")))
                    )
                )
            )
        )
        val withOpusImageBlock = DynamicItem(
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        opus = OpusMajor(
                            contentBlocks = listOf(
                                OpusContentBlock.Image(OpusPic(url = "https://i0.hdslb.com/a.jpg"))
                            )
                        )
                    )
                )
            )
        )
        val textOnly = DynamicItem(
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        opus = OpusMajor(
                            contentBlocks = listOf(OpusContentBlock.Text("仅文字"))
                        )
                    )
                )
            )
        )

        assertTrue(shouldShowDynamicDetailImageLayoutToggle(withDraw))
        assertTrue(shouldShowDynamicDetailImageLayoutToggle(withOpusImageBlock))
        assertFalse(shouldShowDynamicDetailImageLayoutToggle(textOnly))
    }

    @Test
    fun resolveDynamicAuthorClickMid_returnsAuthorMidForRegularUserDynamic() {
        val item = DynamicItem(
            type = "DYNAMIC_TYPE_AV",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 260882L, name = "远古时代装机猿")
            )
        )

        assertEquals(260882L, resolveDynamicAuthorClickMid(item))
    }
    @Test
    fun forwardedHeaderKeepsForwarderWhileNestedHeaderKeepsOriginalAuthor() {
        val original = DynamicItem(
            type = "DYNAMIC_TYPE_AV",
            modules = DynamicModules(module_author = DynamicAuthorModule(mid = 11L, name = "原作者")),
        )
        val forward = DynamicItem(
            type = "DYNAMIC_TYPE_FORWARD",
            modules = DynamicModules(module_author = DynamicAuthorModule(mid = 22L, name = "转发者")),
            orig = original,
        )

        assertEquals(22L, resolveDynamicAuthorClickMid(forward))
        assertEquals(11L, resolveDynamicAuthorClickMid(original))
    }

    @Test
    fun forwardingPgcDoesNotDisableTheRealForwardersHeader() {
        val original = DynamicItem(
            type = "DYNAMIC_TYPE_PGC",
            modules = DynamicModules(module_author = DynamicAuthorModule(mid = 33L, name = "番剧")),
        )
        val forward = DynamicItem(
            type = "DYNAMIC_TYPE_FORWARD",
            modules = DynamicModules(module_author = DynamicAuthorModule(mid = 22L, name = "转发者")),
            orig = original,
        )

        assertEquals(22L, resolveDynamicAuthorClickMid(forward))
        assertNull(resolveDynamicAuthorClickMid(original))
    }
}
