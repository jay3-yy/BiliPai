package com.android.purebilibili.data.model.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchModelsParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun defaultSearch_usesCanonicalKeywordInsteadOfDecoratedHint() {
        val response = json.decodeFromString<SearchDefaultResponse>(
            """{"code":0,"data":{"show_name":"番剧 · 3小时前更新","name":" 番剧 "}}"""
        )

        assertEquals("番剧", response.data?.keyword)
        assertEquals("番剧 · 3小时前更新", response.data?.hint)
    }

    @Test
    fun defaultSearch_doesNotStripUpdateWordsFromCanonicalKeyword() {
        val response = json.decodeFromString<SearchDefaultResponse>(
            """{"code":0,"data":{"name":"3小时前更新是什么梗"}}"""
        )

        assertEquals("3小时前更新是什么梗", response.data?.keyword)
        assertEquals("3小时前更新是什么梗", response.data?.hint)
    }

    @Test
    fun defaultSearch_keepsLegacyHintWhenCanonicalKeywordIsUnavailable() {
        val response = json.decodeFromString<SearchDefaultResponse>(
            """{"code":0,"data":{"show_name":" 旧接口关键词 ","name":" "}}"""
        )

        assertEquals("旧接口关键词", response.data?.keyword)
        assertEquals("旧接口关键词", response.data?.hint)
    }

    @Test
    fun decodeSearchTypeResponse_acceptsStringAndNumericFields() {
        val payload = """
            {
              "code": 0,
              "message": "",
              "data": {
                "page": 1,
                "numPages": 3,
                "numResults": 60,
                "result": [
                  {
                    "id": "12345",
                    "bvid": "BV1xx411c7mD",
                    "title": "test",
                    "pic": "//i0.hdslb.com/test.jpg",
                    "author": "tester",
                    "play": "5678",
                    "video_review": 99,
                    "duration": 180,
                    "pubdate": "1730000000",
                    "mid": "778899"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SearchTypeResponse>(payload)
        val item = response.data?.result?.first()

        assertEquals(12345L, item?.id)
        assertEquals(5678, item?.play)
        assertEquals(99, item?.video_review)
        assertEquals("180", item?.duration)
        assertEquals(1730000000L, item?.pubdate)
        assertEquals(778899L, item?.mid)
    }

    @Test
    fun decodeSearchTypeResponse_acceptsChineseUnitCount() {
        val payload = """
            {
              "code": 0,
              "message": "",
              "data": {
                "page": 1,
                "numPages": 1,
                "numResults": 1,
                "result": [
                  {
                    "id": 1,
                    "bvid": "BV1",
                    "title": "unit",
                    "pic": "",
                    "author": "",
                    "play": "1.2万",
                    "video_review": "3.4万",
                    "duration": "03:21",
                    "pubdate": 0,
                    "mid": 0
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SearchTypeResponse>(payload)
        val item = response.data?.result?.first()

        assertEquals(12000, item?.play)
        assertEquals(34000, item?.video_review)
        assertEquals("03:21", item?.duration)
    }

    @Test
    fun searchVideoItem_preservesHighlightedTitleForUi() {
        val item = SearchVideoItem(
            id = 1L,
            bvid = "BV1",
            title = "这是<em class=\"keyword\">关键词</em>视频",
            pic = "",
            author = "tester"
        )

        val video = item.toVideoItem()

        assertEquals("这是关键词视频", video.title)
        assertEquals("这是<em class=\"keyword\">关键词</em>视频", video.searchHighlightedTitle)
    }

    @Test
    fun decodeSearchResponse_preservesClassroomNavigationFields() {
        val payload = """
            {
              "code": 0,
              "data": {
                "result": [
                  {
                    "result_type": "video",
                    "data": [
                      {
                        "type": "ketang",
                        "id": 37632,
                        "bvid": "",
                        "arcurl": "https://www.bilibili.com/cheese/play/ss37632",
                        "title": "系统课程"
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val video = json.decodeFromString<SearchResponse>(payload)
            .data
            ?.result
            ?.first()
            ?.data
            ?.first()
            ?.toVideoItem()

        assertEquals("ketang", video?.contentType)
        assertEquals("https://www.bilibili.com/cheese/play/ss37632", video?.navigationUrl)
    }

    @Test
    fun decodeSearchTypeResponse_acceptsBlankAndNumericTypeIds() {
        val payload = """
            {
              "code": 0,
              "data": {
                "result": [
                  { "id": 1, "typeid": 24 },
                  { "id": 2, "typeid": "36" },
                  { "id": 3 },
                  { "id": 4, "typeid": 12 },
                  { "id": 5, "typeid": "" }
                ]
              }
            }
        """.trimIndent()

        val items = json.decodeFromString<SearchTypeResponse>(payload)
            .data?.result.orEmpty()

        assertEquals(listOf(24, 36, 0, 12, 0), items.map { it.typeId })
    }

    @Test
    fun decodeSearchArticleResponse_cleansHtmlAndProtocolRelativeImages() {
        val payload = """
            {
              "code": 0,
              "message": "",
              "data": {
                "page": 1,
                "numPages": 2,
                "numResults": 21,
                "result": [
                  {
                    "id": "12345",
                    "mid": "778899",
                    "title": "<em class='keyword'>测试</em>专栏",
                    "desc": "desc",
                    "pub_time": "1730000000",
                    "view": "1.2万",
                    "reply": "345",
                    "like": "567",
                    "image_urls": ["//i0.hdslb.com/article.jpg"],
                    "category_name": "专栏"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SearchArticleResponse>(payload)
        val item = response.data?.result?.first()?.cleanupFields()

        assertEquals(12345L, item?.id)
        assertEquals(778899L, item?.mid)
        assertEquals("测试专栏", item?.title)
        assertEquals(1730000000L, item?.pubTime)
        assertEquals(12000, item?.view)
        assertEquals(345, item?.reply)
        assertEquals(567, item?.like)
        assertEquals("专栏", item?.categoryName)
        assertTrue(item?.imageUrls?.firstOrNull()?.startsWith("https://") == true)
    }

    @Test
    fun decodeSearchTrendingResponse_preservesPinnedRowsAndBadges() {
        val payload = """
            {
              "code": 0,
              "top_list": [
                {
                  "keyword": "中华民族伟大复兴势不可挡",
                  "show_name": "中华民族伟大复兴势不可挡"
                }
              ],
              "list": [
                {
                  "keyword": "Vitality G2",
                  "show_name": "Vitality G2",
                  "show_live_icon": true
                },
                {
                  "keyword": "UP主探访水利院校",
                  "show_name": "UP主探访水利院校",
                  "icon": "//i0.hdslb.com/hot.png",
                  "recommend_reason": "热"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<SearchTrendingResponse>(payload)
        val pinned = response.topList?.firstOrNull()
        val ranked = response.list?.getOrNull(1)

        assertEquals("中华民族伟大复兴势不可挡", pinned?.show_name)
        assertTrue(response.list?.firstOrNull()?.show_live_icon == true)
        assertEquals("//i0.hdslb.com/hot.png", ranked?.icon)
        assertEquals("热", ranked?.recommend_reason)
    }

    @Test
    fun decodeSearchSuggestResponse_acceptsTermAndRichText() {
        val payload = """
            {
              "code": 0,
              "result": {
                "tag": [
                  {
                    "term": "黑神话",
                    "value": "黑神话",
                    "name": "<suggest_high_light>黑神话</suggest_high_light>悟空"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SearchSuggestResponse>(payload)
        val tag = response.result?.tag?.firstOrNull()

        assertEquals("黑神话", tag?.term)
        assertEquals("黑神话", tag?.value)
        assertEquals("<suggest_high_light>黑神话</suggest_high_light>悟空", tag?.name)
    }

    @Test
    fun decodeExtendedSearchTypes_acceptsTopicPhotoAndLiveUser() {
        val topicPayload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "page": 1,
                "numPages": 1,
                "numResults": 1,
                "result": [
                  {
                    "type": "topic",
                    "tp_id": "34958",
                    "title": "<em class='keyword'>BW</em>2025",
                    "description": "topic desc",
                    "cover": "//i0.hdslb.com/topic.jpg",
                    "click": "1.2万",
                    "author": "bilibili"
                  }
                ]
              }
            }
        """.trimIndent()
        val topic = json.decodeFromString<SearchTopicResponse>(topicPayload)
            .data
            ?.result
            ?.first()
            ?.cleanupFields()

        assertEquals(34958L, topic?.topicId)
        assertEquals("BW2025", topic?.title)
        assertEquals("https://i0.hdslb.com/topic.jpg", topic?.cover)
        assertEquals(12000, topic?.view)

        val photoPayload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "page": 1,
                "numPages": 2,
                "numResults": 30,
                "result": [
                  {
                    "type": "photo",
                    "id": "99184721",
                    "mid": "813818",
                    "title": "<em class='keyword'>旅行</em>",
                    "cover": "http://i0.hdslb.com/album.jpg",
                    "uname": "QYS3",
                    "count": "4",
                    "view": "100924",
                    "like": "42"
                  }
                ]
              }
            }
        """.trimIndent()
        val photo = json.decodeFromString<SearchPhotoResponse>(photoPayload)
            .data
            ?.result
            ?.first()
            ?.cleanupFields()

        assertEquals(99184721L, photo?.id)
        assertEquals("旅行", photo?.title)
        assertEquals("https://i0.hdslb.com/album.jpg", photo?.cover)
        assertEquals(4, photo?.count)

        val liveUserPayload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "page": 1,
                "numPages": 1,
                "numResults": 1,
                "result": [
                  {
                    "type": "live_user",
                    "uid": "322892",
                    "roomid": "5441",
                    "uname": "<em class='keyword'>主播</em>",
                    "uface": "//i2.hdslb.com/face.jpg",
                    "live_status": 1,
                    "is_live": true,
                    "attentions": "2570790"
                  }
                ]
              }
            }
        """.trimIndent()
        val liveUser = json.decodeFromString<SearchLiveUserResponse>(liveUserPayload)
            .data
            ?.result
            ?.first()
            ?.cleanupFields()

        assertEquals(322892L, liveUser?.uid)
        assertEquals(5441L, liveUser?.roomid)
        assertEquals("主播", liveUser?.uname)
        assertEquals("https://i2.hdslb.com/face.jpg", liveUser?.uface)
        assertTrue(liveUser?.isLive == true)
    }

    @Test
    fun searchTypeFromValue_recognizesExtendedTypes() {
        assertEquals(SearchType.TOPIC, SearchType.fromValue("topic"))
        assertEquals(SearchType.PHOTO, SearchType.fromValue("photo"))
        assertEquals(SearchType.LIVE_USER, SearchType.fromValue("live_user"))
    }
}
