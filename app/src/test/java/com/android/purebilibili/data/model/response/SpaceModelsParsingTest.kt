package com.android.purebilibili.data.model.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaceModelsParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun decodeSpaceArticleResponse_acceptsWbiArticleShape() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "articles": [
                  {
                    "id": 123,
                    "title": "专栏标题",
                    "summary": "摘要",
                    "image_urls": ["https://i0.hdslb.com/bfs/article/a.jpg"],
                    "stats": {
                      "view": 456,
                      "like": 78
                    }
                  }
                ],
                "count": 9
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceArticleResponse>(payload)
        val article = response.data?.lists?.single()

        assertEquals(9, response.data?.total)
        assertEquals(123L, article?.id)
        assertEquals("专栏标题", article?.title)
        assertEquals(456, article?.stats?.view)
        assertEquals(78, article?.stats?.like)
        assertEquals(listOf("https://i0.hdslb.com/bfs/article/a.jpg"), article?.displayImageUrls())
    }

    @Test
    fun decodeSpaceArticleResponse_acceptsOpusFeedShapeWithoutBlankRows() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "items": [
                  {
                    "opus_id": "1056353752004427792",
                    "content": "通过 DevTools 绕过 SSR 抓包某站专栏正文接口",
                    "cover": {
                      "url": "http://i0.hdslb.com/bfs/article/cover.jpg"
                    },
                    "jump_url": "//www.bilibili.com/opus/1056353752004427792",
                    "stat": {
                      "like": "3",
                      "view": "120"
                    }
                  }
                ],
                "has_more": true,
                "offset": "1056353752004427792"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceArticleResponse>(payload)
        val article = response.data?.lists?.single()

        assertEquals(1056353752004427792L, article?.id)
        assertEquals("通过 DevTools 绕过 SSR 抓包某站专栏正文接口", article?.title)
        assertEquals(120, article?.stats?.view)
        assertEquals(3, article?.stats?.like)
        assertEquals(true, response.data?.has_more)
        assertEquals("1056353752004427792", response.data?.offset)
        assertEquals("//www.bilibili.com/opus/1056353752004427792", article?.jump_url)
        assertEquals(listOf("http://i0.hdslb.com/bfs/article/cover.jpg"), article?.displayImageUrls())
    }

    @Test
    fun decodeSpaceVideoResponse_acceptsChargingArcFields() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "list": {
                  "vlist": [
                    {
                      "aid": 123,
                      "bvid": "BV1xx411c7mD",
                      "title": "充电视频",
                      "is_charging_arc": true,
                      "elec_arc_type": 1,
                      "is_ugcpay": true,
                      "ugc_pay": 1,
                      "ugc_pay_preview": 0
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceVideoResponse>(payload)
        val video = response.data?.list?.vlist?.single()

        assertEquals(true, video?.isChargingArc)
        assertEquals(1, video?.elecArcType)
        assertEquals(true, video?.isUgcpay)
        assertEquals(1, video?.ugcPay)
    }

    @Test
    fun decodeSeasonsSeriesListResponse_keepsEmbeddedArchivesForSpaceCollections() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "items_lists": {
                  "seasons_list": [
                    {
                      "archives": [
                        {
                          "aid": 1001,
                          "bvid": "BV1season",
                          "title": "合集单集",
                          "pic": "https://i0.hdslb.com/bfs/archive/season.jpg",
                          "duration": 180,
                          "pubdate": 1715427472,
                          "stat": {
                            "view": 123,
                            "danmaku": 4
                          }
                        }
                      ],
                      "meta": {
                        "season_id": 725909,
                        "name": "合集·瓦棚市",
                        "cover": "https://i0.hdslb.com/bfs/archive/cover.jpg",
                        "total": 58,
                        "mid": 2766964
                      },
                      "recent_aids": [1001]
                    }
                  ],
                  "series_list": [
                    {
                      "archives": [
                        {
                          "aid": 2001,
                          "bvid": "BV1series",
                          "title": "系列单集"
                        }
                      ],
                      "meta": {
                        "series_id": 12,
                        "name": "系列",
                        "total": 1,
                        "mid": 2766964
                      },
                      "recent_aids": [2001]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SeasonsSeriesListResponse>(payload)
        val season = response.data?.items_lists?.seasons_list?.single()
        val series = response.data?.items_lists?.series_list?.single()

        assertEquals(725909L, season?.meta?.season_id)
        assertEquals("BV1season", season?.archives?.single()?.bvid)
        assertEquals(123L, season?.archives?.single()?.stat?.view)
        assertEquals(12L, series?.meta?.series_id)
        assertEquals("BV1series", series?.archives?.single()?.bvid)
    }

    @Test
    fun decodeSpaceDynamicResponse_acceptsArticleMajorFromSpaceDynamic() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "items": [
                  {
                    "basic": {
                      "comment_id_str": "1200069469486972932",
                      "comment_type": 17,
                      "rid_str": "1200069469486972932"
                    },
                    "id_str": "1200069469486972932",
                    "type": "DYNAMIC_TYPE_ARTICLE",
                    "modules": {
                      "module_dynamic": {
                        "major": {
                          "type": "MAJOR_TYPE_ARTICLE",
                          "article": {
                            "id": 1200069469486972932,
                            "title": "长图文标题",
                            "desc": "完整长图文摘要",
                            "covers": [
                              "https://i0.hdslb.com/bfs/article/cover-a.jpg",
                              "https://i0.hdslb.com/bfs/article/cover-b.jpg"
                            ],
                            "jump_url": "https://www.bilibili.com/opus/1200069469486972932"
                          }
                        }
                      },
                      "module_stat": {
                        "comment": {
                          "count": "5",
                          "forbidden": false
                        },
                        "forward": {
                          "count": 2,
                          "forbidden": false
                        },
                        "like": {
                          "count": "1.2万",
                          "forbidden": false
                        }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceDynamicResponse>(payload)
        val item = response.data?.items?.single()
        val article = item
            ?.modules
            ?.module_dynamic
            ?.major
            ?.article

        assertEquals("1200069469486972932", item?.basic?.comment_id_str)
        assertEquals(17, item?.basic?.comment_type)
        assertEquals("1200069469486972932", item?.basic?.rid_str)
        assertEquals(5, item?.modules?.module_stat?.comment?.count)
        assertEquals(2, item?.modules?.module_stat?.forward?.count)
        assertEquals(12000, item?.modules?.module_stat?.like?.count)
        assertEquals(1200069469486972932L, article?.id)
        assertEquals("长图文标题", article?.title)
        assertEquals("完整长图文摘要", article?.desc)
        assertEquals(
            listOf(
                "https://i0.hdslb.com/bfs/article/cover-a.jpg",
                "https://i0.hdslb.com/bfs/article/cover-b.jpg"
            ),
            article?.covers
        )
    }

    @Test
    fun decodeSpaceDynamicResponse_acceptsOpusPicUrlAlias() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "items": [
                  {
                    "id_str": "1200069469486972932",
                    "type": "DYNAMIC_TYPE_ARTICLE",
                    "modules": {
                      "module_dynamic": {
                        "major": {
                          "type": "MAJOR_TYPE_OPUS",
                          "opus": {
                            "title": "TDS REVIEW",
                            "summary": {
                              "text": "左宫羽 聆川 混合单元入耳耳机体验"
                            },
                            "pics": [
                              {
                                "url": "https://i0.hdslb.com/bfs/new_dyn/tds-opus.jpg",
                                "width": 1200,
                                "height": 800
                              }
                            ]
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceDynamicResponse>(payload)
        val pic = response.data?.items?.single()
            ?.modules
            ?.module_dynamic
            ?.major
            ?.opus
            ?.pics
            ?.single()

        assertEquals("https://i0.hdslb.com/bfs/new_dyn/tds-opus.jpg", pic?.src)
        assertEquals(1200, pic?.width)
        assertEquals(800, pic?.height)
    }

    @Test
    fun decodeSpaceDynamicResponse_acceptsArchiveChargeBadge() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "items": [
                  {
                    "id_str": "1200000000000000000",
                    "type": "DYNAMIC_TYPE_AV",
                    "modules": {
                      "module_dynamic": {
                        "major": {
                          "type": "MAJOR_TYPE_ARCHIVE",
                          "archive": {
                            "aid": "123",
                            "bvid": "BV1xx411c7mD",
                            "title": "空间充电动态",
                            "badge": {
                              "text": "充电专属"
                            },
                            "is_charging_arc": true,
                            "elec_arc_type": 1
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceDynamicResponse>(payload)
        val archive = response.data?.items?.single()
            ?.modules
            ?.module_dynamic
            ?.major
            ?.archive

        assertEquals("充电专属", archive?.badge?.text)
        assertEquals(true, archive?.isChargingArc)
        assertEquals(1, archive?.elecArcType)
    }

    @Test
    fun decodeSpaceDynamicResponse_acceptsForwardOrigItem() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "items": [
                  {
                    "id_str": "forward-id",
                    "type": "DYNAMIC_TYPE_FORWARD",
                    "modules": {
                      "module_author": {
                        "name": "转发用户",
                        "face": "https://i0.hdslb.com/bfs/face/user.jpg",
                        "pub_time": "2026年05月29日"
                      },
                      "module_dynamic": {
                        "desc": {
                          "text": "转发动态"
                        }
                      },
                      "module_stat": {
                        "forward": { "count": 1, "hidden": false },
                        "comment": { "count": "2", "hidden": false },
                        "like": { "count": "1.2万", "status": true }
                      }
                    },
                    "orig": {
                      "id_str": "orig-id",
                      "type": "DYNAMIC_TYPE_DRAW",
                      "modules": {
                        "module_author": {
                          "name": "影视飓风",
                          "face": "https://i0.hdslb.com/bfs/face/orig.jpg"
                        },
                        "module_dynamic": {
                          "desc": {
                            "rich_text_nodes": [
                              { "type": "RICH_TEXT_NODE_TYPE_AT", "orig_text": "@影视飓风 " },
                              { "type": "RICH_TEXT_NODE_TYPE_LOTTERY", "text": "互动抽奖" }
                            ],
                            "text": "@影视飓风 互动抽奖"
                          },
                          "major": {
                            "type": "MAJOR_TYPE_DRAW",
                            "draw": {
                              "items": [
                                {
                                  "src": "https://i0.hdslb.com/bfs/new_dyn/orig.jpg",
                                  "width": 1080,
                                  "height": 1080
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceDynamicResponse>(payload)
        val item = response.data?.items?.single()
        val orig = item?.orig

        assertEquals("DYNAMIC_TYPE_FORWARD", item?.type)
        assertEquals("转发用户", item?.modules?.module_author?.name)
        assertEquals(12000, item?.modules?.module_stat?.like?.count)
        assertEquals(true, item?.modules?.module_stat?.like?.status)
        assertEquals("orig-id", orig?.id_str)
        assertEquals("影视飓风", orig?.modules?.module_author?.name)
        assertEquals(
            "https://i0.hdslb.com/bfs/new_dyn/orig.jpg",
            orig?.modules?.module_dynamic?.major?.draw?.items?.single()?.src
        )
        assertEquals(
            "@影视飓风 ",
            orig?.modules?.module_dynamic?.desc?.rich_text_nodes?.first()?.orig_text
        )
    }

    @Test
    fun decodeSpaceDynamicResponse_acceptsDeleteThreePointItem() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "items": [
                  {
                    "id_str": "1063487284684259332",
                    "type": "DYNAMIC_TYPE_FORWARD",
                    "modules": {
                      "module_more": {
                        "three_point_items": [
                          {
                            "label": "删除",
                            "modal": {
                              "cancel": "取消",
                              "confirm": "确认删除",
                              "content": "动态删除后将无法恢复，请谨慎操作",
                              "title": "要删除动态吗？"
                            },
                            "params": {
                              "dyn_id_str": "1063487284684259332",
                              "dyn_type": 1,
                              "rid_str": "1063487284684259332"
                            },
                            "type": "THREE_POINT_DELETE"
                          }
                        ]
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SpaceDynamicResponse>(payload)
        val deleteItem = response.data
            ?.items
            ?.single()
            ?.modules
            ?.module_more
            ?.three_point_items
            ?.single()

        assertEquals("THREE_POINT_DELETE", deleteItem?.type)
        assertEquals("确认删除", deleteItem?.modal?.confirm)
        assertEquals("1063487284684259332", deleteItem?.params?.dyn_id_str)
        assertEquals(1, deleteItem?.params?.dyn_type)
    }

    @Test
    fun decodeSpaceDynamicResponse_preservesStandaloneTopicMetadata() {
        val payload = """
            {
              "code": 0,
              "data": {
                "items": [
                  {
                    "id_str": "123456",
                    "type": "DYNAMIC_TYPE_WORD",
                    "modules": {
                      "module_dynamic": {
                        "topic": {
                          "id": 1314000,
                          "name": "新机来了！"
                        }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val topic = json.decodeFromString<SpaceDynamicResponse>(payload)
            .data?.items?.single()?.modules?.module_dynamic?.topic

        assertEquals(1314000L, topic?.id)
        assertEquals("新机来了！", topic?.name)
    }

    @Test
    fun decodeSpaceDynamicResponse_preservesPinnedModuleTag() {
        val payload = """
            {
              "code": 0,
              "data": {
                "items": [
                  {
                    "id_str": "123456",
                    "type": "DYNAMIC_TYPE_WORD",
                    "modules": {
                      "module_tag": {
                        "text": "置顶"
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val tag = json.decodeFromString<SpaceDynamicResponse>(payload)
            .data?.items?.single()?.modules?.module_tag

        assertEquals("置顶", tag?.text)
    }
}
