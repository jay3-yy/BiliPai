package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.FavoriteSection
import com.android.purebilibili.data.model.response.FavoriteSearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCategoryPolicyTest {
    @Test
    fun `pgc uses poster columns while horizontal categories use personal max extent`() {
        assertEquals(2, resolveFavoriteCategoryColumnCount(FavoriteSection.BANGUMI, 360f))
        assertEquals(4, resolveFavoriteCategoryColumnCount(FavoriteSection.CINEMA, 840f))
        assertEquals(1, resolveFavoriteCategoryColumnCount(FavoriteSection.ARTICLE, 479f))
        assertEquals(2, resolveFavoriteCategoryColumnCount(FavoriteSection.COURSE, 840f))
    }

    @Test
    fun `batch and navigation capability follows section semantics`() {
        assertTrue(supportsFavoriteCategoryBatchSelection(FavoriteSection.BANGUMI))
        assertTrue(supportsFavoriteCategoryBatchSelection(FavoriteSection.NOTE))
        assertFalse(supportsFavoriteCategoryBatchSelection(FavoriteSection.ARTICLE))
        assertTrue(opensFavoriteCategoryInNativeScreen(FavoriteSection.TOPIC))
        assertEquals(0, resolveFavoriteSearchApiType(FavoriteSearchScope.CURRENT_FOLDER))
        assertEquals(1, resolveFavoriteSearchApiType(FavoriteSearchScope.ALL_VIDEO_FOLDERS))
    }
}
