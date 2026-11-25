package com.android.purebilibili.feature.profile

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.DynamicMoreModule
import com.android.purebilibili.data.model.response.DynamicThreePointItem
import com.android.purebilibili.data.model.response.DynamicThreePointModal
import com.android.purebilibili.data.model.response.DynamicThreePointParams
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceAggregateData
import com.android.purebilibili.data.model.response.SpaceAggregateFavoriteItem
import com.android.purebilibili.data.model.response.SpaceAggregateFavoriteSection
import com.android.purebilibili.data.model.response.SpaceDynamicAuthor
import com.android.purebilibili.data.model.response.SpaceDynamicContent
import com.android.purebilibili.data.model.response.SpaceDynamicDesc
import com.android.purebilibili.data.model.response.SpaceDynamicDraw
import com.android.purebilibili.data.model.response.SpaceDynamicDrawItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.SpaceDynamicMajor
import com.android.purebilibili.data.model.response.SpaceDynamicModules
import com.android.purebilibili.data.model.response.SpaceDynamicOpus
import com.android.purebilibili.data.model.response.SpaceDynamicRichText
import com.android.purebilibili.data.model.response.SpaceVideoItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileSpacePolicyTest {

    @Test
    fun `home uses aggregate video preview without loading the full contribution or dynamic lists`() {
        val space = ProfileSpaceUiState(contributionVideos = persistentListOf(SpaceVideoItem(bvid = "BVpreview")))
        assertEquals(setOf(ProfileSpaceMainTab.FAVORITE, ProfileSpaceMainTab.BANGUMI), resolveProfileContentLoadTargets(space))
        assertEquals(setOf(ProfileSpaceMainTab.CONTRIBUTION, ProfileSpaceMainTab.FAVORITE, ProfileSpaceMainTab.BANGUMI),
            resolveProfileContentLoadTargets(space.copy(contributionVideos = persistentListOf())))
    }

    @Test
    fun `each personal content tab only requests its own data`() {
        for (tab in ProfileSpaceMainTab.entries.filter { it != ProfileSpaceMainTab.HOME }) {
            assertEquals(setOf(tab), resolveProfileContentLoadTargets(ProfileSpaceUiState(selectedTab = tab)))
        }
    }

    @Test
    fun `profile space tabs match self space order`() {
        assertEquals(
            listOf("主页", "动态", "投稿", "收藏", "番剧"),
            defaultProfileSpaceTabs().map { it.title }
        )
        assertEquals(ProfileSpaceMainTab.HOME, defaultProfileSpaceTabs().first().tab)
        assertEquals(ProfileSpaceMainTab.BANGUMI, defaultProfileSpaceTabs().last().tab)
    }

    @Test
    fun `home sections keep reference order and hide empty sections`() {
        val sections = resolveProfileSpaceHomeSections(
            favoriteFolders = listOf(FavFolder(id = 1, title = "默认收藏夹", media_count = 8)),
            bangumiItems = listOf(FollowBangumiItem(seasonId = 2, title = "追番")),
            coinVideos = listOf(SpaceAggregateArchiveItem(aid = 3, title = "投币")),
            likeVideos = listOf(SpaceAggregateArchiveItem(aid = 4, title = "点赞")),
            contributionVideos = listOf(SpaceVideoItem(aid = 5, bvid = "BV1xx", title = "投稿"))
        )

        assertEquals(
            listOf(
                ProfileSpaceHomeSection.FAVORITES,
                ProfileSpaceHomeSection.BANGUMI,
                ProfileSpaceHomeSection.COIN_VIDEOS,
                ProfileSpaceHomeSection.LIKE_VIDEOS,
                ProfileSpaceHomeSection.CONTRIBUTIONS,
                ProfileSpaceHomeSection.SERVICES
            ),
            sections
        )
    }

    @Test
    fun `home sections always keep services even when content is empty`() {
        assertEquals(
            listOf(ProfileSpaceHomeSection.SERVICES),
            resolveProfileSpaceHomeSections(
                favoriteFolders = emptyList(),
                bangumiItems = emptyList(),
                coinVideos = emptyList(),
                likeVideos = emptyList(),
                contributionVideos = emptyList()
            )
        )
    }

    @Test
    fun `dashboard owned sections are not repeated below personal dashboard`() {
        assertEquals(
            listOf(
                ProfileSpaceHomeSection.BANGUMI,
                ProfileSpaceHomeSection.CONTRIBUTIONS,
            ),
            resolveProfileSpaceHomeSections(
                favoriteFolders = listOf(FavFolder(id = 1, title = "默认收藏夹")),
                bangumiItems = listOf(FollowBangumiItem(seasonId = 2, title = "追番")),
                coinVideos = emptyList(),
                likeVideos = emptyList(),
                contributionVideos = listOf(SpaceVideoItem(aid = 5, bvid = "BV1xx", title = "投稿")),
                includeDashboardOwnedSections = false,
            ),
        )
    }

    @Test
    fun `editable account state trims sign and marks only sign as editable`() {
        val state = ProfileEditableAccountState(
            name = "测试用户",
            birthday = "2000-01-01",
            sex = "保密",
            sign = "  这个人很神秘  "
        )

        assertEquals("这个人很神秘", state.normalizedSign)
        assertTrue(state.canSubmitSign)
        assertFalse(state.canEditName)
        assertFalse(state.canEditBirthday)
        assertFalse(state.canEditSex)
    }

    @Test
    fun `sign validation allows empty sign and blocks over length`() {
        assertEquals(null, validateProfileSign(""))
        assertEquals(null, validateProfileSign("a".repeat(70)))
        assertEquals("签名最多支持 70 个字符", validateProfileSign("a".repeat(71)))
    }

    @Test
    fun `identity meta keeps real sign and normalizes ip location`() {
        val meta = resolveProfileSpaceIdentityMeta(
            sign = "  好  ",
            ipLocation = "IP属地：广东",
            sex = "男"
        )

        assertEquals("好", meta.signText)
        assertFalse(meta.signPlaceholder)
        assertEquals("IP 属地 · 广东", meta.ipText)
        assertEquals("男", meta.sexText)
    }

    @Test
    fun `identity meta uses readable private fallback and hides private sex`() {
        val meta = resolveProfileSpaceIdentityMeta(
            sign = " ",
            ipLocation = null,
            sex = "保密"
        )

        assertEquals("这个人很神秘，什么都没有写", meta.signText)
        assertTrue(meta.signPlaceholder)
        assertEquals("IP 属地 · 保密", meta.ipText)
        assertEquals(null, meta.sexText)
    }

    @Test
    fun `space favorite folders keep cover from aggregate response`() {
        val state = resolveProfileSpaceStateFromAggregate(
            aggregate = SpaceAggregateData(
                favourite2 = SpaceAggregateFavoriteSection(
                    count = 1,
                    item = listOf(
                        SpaceAggregateFavoriteItem(
                            mediaId = 123,
                            title = "默认收藏夹",
                            cover = "https://i0.hdslb.com/bfs/archive/folder.jpg",
                            media_count = 9
                        )
                    )
                )
            ),
            favoriteFoldersFallback = emptyList(),
            bangumiItems = emptyList(),
            dynamicItems = emptyList()
        )

        assertEquals("https://i0.hdslb.com/bfs/archive/folder.jpg", state.favoriteFolders.first().cover)
    }

    @Test
    fun `space favorite folders read cover alias from aggregate response`() {
        val json = Json { ignoreUnknownKeys = true }
        val aggregate = json.decodeFromString<SpaceAggregateData>(
            """
            {
              "favourite2": {
                "count": 1,
                "item": [
                  {
                    "media_id": 123,
                    "title": "默认收藏夹",
                    "pic": "https://i0.hdslb.com/bfs/archive/folder-alias.jpg",
                    "media_count": 9
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val state = resolveProfileSpaceStateFromAggregate(
            aggregate = aggregate,
            favoriteFoldersFallback = emptyList(),
            bangumiItems = emptyList(),
            dynamicItems = emptyList()
        )

        assertEquals("https://i0.hdslb.com/bfs/archive/folder-alias.jpg", state.favoriteFolders.first().cover)
    }

    @Test
    fun `aggregate merge preserves already loaded bangumi and dynamic content`() {
        val bangumi = FollowBangumiItem(seasonId = 2, title = "追番")
        val dynamic = SpaceDynamicItem(id_str = "dynamic-1")
        val current = ProfileSpaceUiState(
            bangumiItems = persistentListOf(bangumi),
            bangumiCount = 1,
            dynamicItems = persistentListOf(dynamic),
            isLoading = true
        )

        val merged = mergeProfileAggregateState(
            current = current,
            aggregate = SpaceAggregateData(
                archive = com.android.purebilibili.data.model.response.SpaceAggregateArchive(
                    count = 1,
                    item = listOf(SpaceAggregateArchiveItem(aid = 3, title = "投稿"))
                )
            )
        )

        assertEquals(listOf(bangumi), merged.bangumiItems)
        assertEquals(listOf(dynamic), merged.dynamicItems)
        assertEquals(1, merged.contributionVideoCount)
        assertTrue(merged.isLoading)
    }

    @Test
    fun `profile contribution hydration policy still covers missing aggregate items`() {
        assertTrue(
            shouldHydrateProfileContributionVideos(
                contributionVideoCount = 5,
                seededVideoCount = 0
            )
        )
        assertTrue(
            shouldHydrateProfileContributionVideos(
                contributionVideoCount = 40,
                seededVideoCount = 10
            )
        )
        assertFalse(
            shouldHydrateProfileContributionVideos(
                contributionVideoCount = 5,
                seededVideoCount = 5
            )
        )
        assertFalse(
            shouldHydrateProfileContributionVideos(
                contributionVideoCount = 0,
                seededVideoCount = 0
            )
        )
    }

    @Test
    fun `profile contribution content distinguishes loading empty and failure`() {
        assertEquals(
            ProfileContributionContentState.LOADING,
            resolveProfileContributionContentState(ProfileContributionLoadState.LOADING, hasVideos = false)
        )
        assertEquals(
            ProfileContributionContentState.EMPTY,
            resolveProfileContributionContentState(ProfileContributionLoadState.LOADED, hasVideos = false)
        )
        assertEquals(
            ProfileContributionContentState.ERROR,
            resolveProfileContributionContentState(ProfileContributionLoadState.ERROR, hasVideos = false)
        )
        assertEquals(
            ProfileContributionContentState.CONTENT,
            resolveProfileContributionContentState(ProfileContributionLoadState.ERROR, hasVideos = true)
        )
    }

    @Test
    fun `hydrated contribution result remains authoritative over late aggregate`() {
        val hydrated = ProfileSpaceUiState(
            contributionVideos = persistentListOf(SpaceVideoItem(aid = 9, bvid = "BV9", title = "WBI 投稿")),
            contributionVideoCount = 1,
            contributionLoadState = ProfileContributionLoadState.LOADED
        )

        val merged = mergeProfileAggregateState(
            current = hydrated,
            aggregate = SpaceAggregateData(
                archive = com.android.purebilibili.data.model.response.SpaceAggregateArchive(
                    count = 0,
                    item = emptyList()
                )
            )
        )

        assertEquals(hydrated.contributionVideos, merged.contributionVideos)
        assertEquals(1, merged.contributionVideoCount)
    }

    @Test
    fun `successful empty contribution response clears aggregate seed`() {
        val seeded = ProfileSpaceUiState(
            contributionVideos = persistentListOf(SpaceVideoItem(aid = 3, bvid = "BV3", title = "聚合投稿")),
            contributionVideoCount = 1,
            contributionLoadState = ProfileContributionLoadState.LOADING
        )

        val merged = mergeProfileContributionVideoState(seeded, videos = emptyList(), totalCount = 0)

        assertTrue(merged.contributionVideos.isEmpty())
        assertEquals(0, merged.contributionVideoCount)
        assertEquals(ProfileContributionLoadState.LOADED, merged.contributionLoadState)
    }

    @Test
    fun `profile contribution merge prefers hydrated videos`() {
        val current = ProfileSpaceUiState(
            contributionVideos = persistentListOf(),
            contributionVideoCount = 3
        )
        val hydrated = listOf(
            SpaceVideoItem(aid = 1, bvid = "BV1", title = "自己的投稿")
        )
        val merged = mergeProfileContributionVideoState(
            current = current,
            videos = hydrated,
            totalCount = 3
        )
        assertEquals(hydrated, merged.contributionVideos)
        assertEquals(3, merged.contributionVideoCount)
    }

    @Test
    fun `favorite merge keeps aggregate cover without requesting folder content`() {
        val current = ProfileSpaceUiState(
            favoriteFolders = persistentListOf(
                FavFolder(
                    id = 10,
                    title = "默认收藏夹",
                    cover = "https://i0.hdslb.com/folder.jpg",
                    media_count = 3
                )
            ),
            favoriteFolderCount = 1
        )

        val merged = mergeProfileFavoriteFolderState(
            current = current,
            folders = listOf(
                FavFolder(id = 10, title = "默认收藏夹", cover = "", media_count = 3),
                FavFolder(id = 11, title = "音乐", cover = "", media_count = 1)
            )
        )

        assertEquals(2, merged.favoriteFolders.size)
        assertEquals("https://i0.hdslb.com/folder.jpg", merged.favoriteFolders.first().cover)
        assertEquals("", merged.favoriteFolders.last().cover)
    }

    @Test
    fun `favorite preview cover targets only visible empty covered folders`() {
        val folders = listOf(
            FavFolder(id = 1, title = "已有封面", cover = "https://i0.hdslb.com/cover.jpg", media_count = 3),
            FavFolder(id = 2, title = "空收藏夹", cover = "", media_count = 0),
            FavFolder(id = 3, title = "需要封面", cover = "", media_count = 2),
            FavFolder(id = 4, title = "也需要封面", cover = " ", media_count = 1),
            FavFolder(id = 5, title = "第 5 个", cover = "", media_count = 1),
            FavFolder(id = 6, title = "第 6 个", cover = "", media_count = 1),
            FavFolder(id = 7, title = "第 7 个", cover = "", media_count = 1),
            FavFolder(id = 8, title = "第 8 个", cover = "", media_count = 1)
        )

        assertEquals(
            listOf(3L, 4L, 5L, 6L),
            resolveProfileFavoritePreviewCoverTargets(folders).map { it.mediaId }
        )
    }

    @Test
    fun `favorite preview covers fill only blank folder covers`() {
        val folders = listOf(
            FavFolder(id = 1, title = "已有封面", cover = "https://i0.hdslb.com/existing.jpg", media_count = 3),
            FavFolder(id = 2, title = "需要封面", cover = "", media_count = 2)
        )

        val merged = mergeProfileFavoritePreviewCovers(
            folders = folders,
            coversByMediaId = mapOf(
                1L to "https://i0.hdslb.com/new.jpg",
                2L to "https://i0.hdslb.com/fallback.jpg"
            )
        )

        assertEquals("https://i0.hdslb.com/existing.jpg", merged[0].cover)
        assertEquals("https://i0.hdslb.com/fallback.jpg", merged[1].cover)
    }

    @Test
    fun `profile load generation rejects response from previous account`() {
        assertFalse(
            shouldApplyProfileLoadResult(
                requestGeneration = 2L,
                currentGeneration = 3L,
                requestedMid = 10L,
                currentMid = 20L
            )
        )
        assertTrue(
            shouldApplyProfileLoadResult(
                requestGeneration = 3L,
                currentGeneration = 3L,
                requestedMid = 20L,
                currentMid = 20L
            )
        )
    }

    @Test
    fun `dynamic cover falls back to draw image when archive is absent`() {
        val item = SpaceDynamicItem(
            modules = SpaceDynamicModules(
                module_dynamic = SpaceDynamicContent(
                    major = SpaceDynamicMajor(
                        draw = SpaceDynamicDraw(
                            items = listOf(SpaceDynamicDrawItem(src = "https://i0.hdslb.com/bfs/draw.jpg"))
                        )
                    )
                )
            )
        )

        assertEquals("https://i0.hdslb.com/bfs/draw.jpg", resolveProfileDynamicCover(item))
    }

    @Test
    fun `dynamic image urls include all draw pictures for preview`() {
        val item = SpaceDynamicItem(
            modules = SpaceDynamicModules(
                module_dynamic = SpaceDynamicContent(
                    major = SpaceDynamicMajor(
                        draw = SpaceDynamicDraw(
                            items = listOf(
                                SpaceDynamicDrawItem(src = "https://i0.hdslb.com/bfs/draw-a.jpg"),
                                SpaceDynamicDrawItem(src = "https://i0.hdslb.com/bfs/draw-b.jpg")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "https://i0.hdslb.com/bfs/draw-a.jpg",
                "https://i0.hdslb.com/bfs/draw-b.jpg"
            ),
            resolveProfileDynamicImageUrls(item)
        )
    }

    @Test
    fun `dynamic image urls include opus pictures for preview`() {
        val item = SpaceDynamicItem(
            modules = SpaceDynamicModules(
                module_dynamic = SpaceDynamicContent(
                    major = SpaceDynamicMajor(
                        opus = SpaceDynamicOpus(
                            pics = listOf(
                                SpaceDynamicDrawItem(src = "https://i0.hdslb.com/bfs/opus-a.jpg"),
                                SpaceDynamicDrawItem(src = "https://i0.hdslb.com/bfs/opus-b.jpg")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "https://i0.hdslb.com/bfs/opus-a.jpg",
                "https://i0.hdslb.com/bfs/opus-b.jpg"
            ),
            resolveProfileDynamicImageUrls(item)
        )
    }

    @Test
    fun `dynamic text prefers rich text nodes and keeps mentions`() {
        val item = SpaceDynamicItem(
            modules = SpaceDynamicModules(
                module_author = SpaceDynamicAuthor(name = "测试用户", pub_time = "2026年05月29日"),
                module_dynamic = SpaceDynamicContent(
                    desc = SpaceDynamicDesc(
                        rich_text_nodes = listOf(
                            SpaceDynamicRichText(type = "RICH_TEXT_NODE_TYPE_AT", orig_text = "@影视飓风 "),
                            SpaceDynamicRichText(type = "RICH_TEXT_NODE_TYPE_TEXT", text = "互动抽奖")
                        ),
                        text = "兜底文本"
                    )
                )
            )
        )

        assertEquals("@影视飓风 互动抽奖", resolveProfileDynamicText(item))
        assertEquals("测试用户", resolveProfileDynamicAuthorName(item))
        assertEquals("2026年05月29日", resolveProfileDynamicPublishText(item))
    }

    @Test
    fun `forward dynamic keeps original content cover and action count text`() {
        val item = SpaceDynamicItem(
            type = "DYNAMIC_TYPE_FORWARD",
            orig = SpaceDynamicItem(
                modules = SpaceDynamicModules(
                    module_dynamic = SpaceDynamicContent(
                        major = SpaceDynamicMajor(
                            draw = SpaceDynamicDraw(
                                items = listOf(SpaceDynamicDrawItem(src = "https://i0.hdslb.com/bfs/orig.jpg"))
                            )
                        )
                    )
                )
            )
        )

        assertEquals("https://i0.hdslb.com/bfs/orig.jpg", resolveProfileDynamicCover(item.orig!!))
        assertEquals("点赞 1.2万", resolveProfileDynamicActionText("点赞", 12000))
    }

    @Test
    fun `profile dynamic delete action uses server menu params`() {
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
                            ),
                            modal = DynamicThreePointModal(
                                title = "要删除动态吗？",
                                content = "动态删除后将无法恢复，请谨慎操作",
                                confirm = "确认删除",
                                cancel = "再想想"
                            )
                        )
                    )
                )
            )
        )

        val action = resolveProfileDynamicDeleteAction(item)

        assertEquals("1063487284684259332", action?.dynamicId)
        assertEquals(1, action?.dynType)
        assertEquals("1063487284684259332", action?.rid)
        assertEquals("确认删除", action?.confirmText)
        assertEquals("再想想", action?.cancelText)
    }

    @Test
    fun `tab chrome uses neutral embedded panel styling`() {
        val spec = resolveProfileSpaceTabChromeSpec()
        val panelSpec = resolveProfileSpaceContentPanelSpec()

        assertEquals(0f, spec.rowContainerAlpha)
        assertTrue(spec.controlContainerAlpha in 0.2f..0.3f)
        assertTrue(spec.selectedTextAlpha >= 0.95f)
        assertTrue(spec.unselectedTextAlpha >= 0.65f)
        assertTrue(spec.selectedIndicatorAlpha in 0.12f..0.22f)
        assertEquals(2, spec.rowHorizontalInsetDp)
        assertEquals(2, spec.controlHorizontalInsetDp)
        assertEquals(0, spec.rowVerticalInsetDp)
        assertTrue(panelSpec.topOverlapDp >= 20)
    }

    @Test
    fun `wallpaper chrome palette uses neutral glass over wallpaper luminance`() {
        val darkPalette = resolveProfileSpaceWallpaperChromePalette(
            wallpaperColor = Color(0xFF6E1515),
            fallbackSurfaceColor = Color.White,
            fallbackContentColor = Color.Black
        )
        val lightPalette = resolveProfileSpaceWallpaperChromePalette(
            wallpaperColor = Color(0xFFEADBC5),
            fallbackSurfaceColor = Color.White,
            fallbackContentColor = Color.Black
        )
        val plainPalette = resolveProfileSpaceWallpaperChromePalette(
            wallpaperColor = Color.Transparent,
            fallbackSurfaceColor = Color.White,
            fallbackContentColor = Color.Black
        )

        assertEquals(Color.White, darkPalette.serviceTextColor)
        assertEquals(Color.Black, lightPalette.serviceTextColor)
        assertEquals(Color.Transparent, darkPalette.rowContainerColor)
        assertEquals(Color.Transparent, darkPalette.serviceContainerColor)
        assertTrue(darkPalette.contentPanelColor.alpha in 0.28f..0.4f)
        assertTrue(lightPalette.contentPanelColor.alpha in 0.24f..0.36f)
        assertEquals(Color.White, plainPalette.contentPanelColor)
        assertEquals(Color.Black, plainPalette.sectionTextColor)
    }
}
