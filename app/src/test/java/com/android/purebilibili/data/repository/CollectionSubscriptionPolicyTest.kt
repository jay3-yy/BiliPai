package com.android.purebilibili.data.repository

import com.android.purebilibili.core.store.resolveAccountCollectionSubscriptionState
import com.android.purebilibili.core.store.setAccountCollectionSubscription
import com.android.purebilibili.core.store.toggleAccountCollectionSubscription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CollectionSubscriptionPolicyTest {

    @Test
    fun `subscription request matches BiliPai favorite season endpoint and form fields`() {
        val request = buildCollectionSubscriptionRequest(
            seasonId = 725909L,
            subscribe = true,
            csrf = "csrf-token"
        )

        assertEquals("x/v3/fav/season/fav", request.path)
        assertEquals("web", request.platform)
        assertEquals(725909L, request.seasonId)
        assertEquals("csrf-token", request.csrf)
    }

    @Test
    fun `unsubscription request matches BiliPai unfavorite season endpoint`() {
        val request = buildCollectionSubscriptionRequest(
            seasonId = 725909L,
            subscribe = false,
            csrf = "csrf-token"
        )

        assertEquals("x/v3/fav/season/unfav", request.path)
    }

    @Test
    fun `account-scoped collection subscription keys isolate accounts and ignore legacy unprefixed ids`() {
        val legacySubscribed = setOf("725909")
        assertNull(
            resolveAccountCollectionSubscriptionState(
                subscribedIds = legacySubscribed,
                knownIds = legacySubscribed,
                accountMid = 1001L,
                collectionId = 725909L,
            )
        )
        assertNull(
            resolveAccountCollectionSubscriptionState(
                subscribedIds = legacySubscribed,
                knownIds = legacySubscribed,
                accountMid = null,
                collectionId = 725909L,
            )
        )

        val subscribedForFirst = setAccountCollectionSubscription(
            subscribedCollectionIds = legacySubscribed,
            accountMid = 1001L,
            collectionId = 725909L,
            subscribed = true,
        )
        val knownIds = setOf("1001_725909", "2002_725909")
        assertEquals(
            true,
            resolveAccountCollectionSubscriptionState(
                subscribedIds = subscribedForFirst,
                knownIds = knownIds,
                accountMid = 1001L,
                collectionId = 725909L,
            )
        )
        assertEquals(
            false,
            resolveAccountCollectionSubscriptionState(
                subscribedIds = subscribedForFirst,
                knownIds = knownIds,
                accountMid = 2002L,
                collectionId = 725909L,
            )
        )

        val toggledOff = toggleAccountCollectionSubscription(
            subscribedCollectionIds = subscribedForFirst,
            accountMid = 1001L,
            collectionId = 725909L,
        )
        assertEquals(
            false,
            resolveAccountCollectionSubscriptionState(
                subscribedIds = toggledOff,
                knownIds = knownIds,
                accountMid = 1001L,
                collectionId = 725909L,
            )
        )
    }

    @Test
    fun `video relation cache key normalizes aid and bvid for same session and isolates sessions`() {
        val sessionA = "account-a"
        val sessionB = "account-b"
        val fromBoth = buildVideoRelationKey(session = sessionA, bvid = "BV17x411w7KC", aid = 170001L)
        val fromBvidOnly = buildVideoRelationKey(session = sessionA, bvid = "BV17x411w7KC", aid = 0L)
        val fromAidOnly = buildVideoRelationKey(session = sessionA, bvid = null, aid = 170001L)
        val otherAccount = buildVideoRelationKey(session = sessionB, bvid = "BV17x411w7KC", aid = 170001L)

        assertEquals(fromBoth, fromBvidOnly)
        assertEquals(fromBoth, fromAidOnly)
        assertNotEquals(fromBoth, otherAccount)
    }
}
