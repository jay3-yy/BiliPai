package com.android.purebilibili.navigation3

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import com.android.purebilibili.navigation.ScreenRoutes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BiliPaiNavKeyPolymorphicSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun argumentlessWindowRoutesCanBeSavedAsRootDestinations() {
        val entries = listOf(
            ScreenRoutes.Search.route to BiliPaiNavKey.Search(),
            ScreenRoutes.LikedVideos.route to BiliPaiNavKey.LikedVideos(),
        )
        for ((route, expected) in entries) {
            val stack = NavBackStack(listOf(legacyRouteToBiliPaiNavKey(route)).toMutableStateList())
            val saved = with(BiliPaiNavBackStackSaver) { SaverScope { true }.save(stack) }

            assertEquals(
                listOf(expected),
                assertNotNull(BiliPaiNavBackStackSaver.restore(assertNotNull(saved))).toList(),
            )
        }
    }

    @Test
    fun savedRouteListRestoresIntoAndroidXStackAndRetainsItsWireFormat() {
        val routes: List<BiliPaiNavKey> = listOf(
            BiliPaiNavKey.MainHost,
            BiliPaiNavKey.Search(keyword = "折叠屏", openId = 41L),
            BiliPaiNavKey.VideoDetail(bvid = "BV1", cid = 2L, openId = 42L, resumePositionMs = 6500L),
        )
        val serializer = ListSerializer(BiliPaiNavKey.serializer())
        val savedRoutes = json.encodeToString(serializer, routes)

        val stack = assertNotNull(BiliPaiNavBackStackSaver.restore(savedRoutes))
        assertEquals(routes, stack.toList())
        assertEquals(routes.last(), stack.removeAt(stack.lastIndex))

        val savedAfterBack = with(BiliPaiNavBackStackSaver) { SaverScope { true }.save(stack) }
        assertEquals(routes.dropLast(1), json.decodeFromString(serializer, assertNotNull(savedAfterBack)))
    }

    @Test
    fun dynamicCommentEntrySurvivesSavedBackStackRestoration() {
        val stack: List<BiliPaiNavKey> = listOf(
            BiliPaiNavKey.MainHost,
            BiliPaiNavKey.DynamicDetail("123"),
            BiliPaiNavKey.DynamicDetail("123", openComments = true),
            BiliPaiNavKey.DynamicDetail("123", 456L, 789L, openComments = true),
        )
        val encoded = json.encodeToString(ListSerializer(BiliPaiNavKey.serializer()), stack)
        val decoded = json.decodeFromString(ListSerializer(BiliPaiNavKey.serializer()), encoded)
        assertEquals(stack, decoded)
    }

    @Test
    fun `back stack with SeasonSeriesDetail round trips`() {
        // 回归：SeasonSeriesDetail.type 与默认 class discriminator "type" 冲突，
        // onSaveInstanceState 保存导航栈时曾抛 JsonEncodingException 闪退。
        val stack: List<BiliPaiNavKey> = listOf(
            BiliPaiNavKey.MainHost,
            BiliPaiNavKey.SeasonSeriesDetail(
                type = "season",
                id = 123L,
                mid = 456L,
                title = "合集",
                ownerName = "UP主"
            ),
            BiliPaiNavKey.VideoDetail(bvid = "BV1", cid = 2L),
        )

        val encoded = json.encodeToString(ListSerializer(BiliPaiNavKey.serializer()), stack)

        // 自定义 discriminator 不与任何子类属性名冲突
        assertTrue(encoded.contains("\"nav_key_class\""))
        assertTrue(encoded.contains("\"type\":\"season\""))

        val decoded = json.decodeFromString(ListSerializer(BiliPaiNavKey.serializer()), encoded)
        assertEquals(stack, decoded)
    }
}
