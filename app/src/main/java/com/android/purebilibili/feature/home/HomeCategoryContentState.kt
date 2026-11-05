package com.android.purebilibili.feature.home

import kotlinx.collections.immutable.toPersistentMap

/** 热门内容只认子分类缓存；分类镜像与当前页字段在同一次状态发布中同步。 */
internal fun HomeUiState.homeCategoryContent(
    category: HomeCategory,
    subCategory: PopularSubCategory = popularSubCategory,
): CategoryContent = if (category == HomeCategory.POPULAR) {
    popularCategoryStates[subCategory] ?: CategoryContent()
} else {
    categoryStates[category] ?: CategoryContent()
}

private fun HomeUiState.projectCurrentHomeContent(): HomeUiState {
    val content = homeCategoryContent(currentCategory)
    return copy(
        videos = content.videos,
        liveRooms = content.liveRooms,
        followedLiveRooms = content.followedLiveRooms,
        isLoading = content.isLoading,
        error = content.error,
    )
}

internal fun HomeUiState.selectHomeCategory(category: HomeCategory): HomeUiState {
    val selected = copy(currentCategory = category)
    return if (category == HomeCategory.POPULAR) {
        selected.selectHomePopularSubCategory(popularSubCategory)
    } else selected.projectCurrentHomeContent()
}

internal fun HomeUiState.selectHomePopularSubCategory(subCategory: PopularSubCategory): HomeUiState {
    val selected = copy(
        popularSubCategory = subCategory,
        categoryStates = categoryStates.toPersistentMap().put(HomeCategory.POPULAR, homeCategoryContent(HomeCategory.POPULAR, subCategory)),
    )
    return if (currentCategory == HomeCategory.POPULAR) selected.projectCurrentHomeContent() else selected
}

internal fun HomeUiState.updateHomePopularContent(
    subCategory: PopularSubCategory,
    transform: (CategoryContent) -> CategoryContent,
): HomeUiState {
    val content = transform(homeCategoryContent(HomeCategory.POPULAR, subCategory))
    val updated = copy(popularCategoryStates = popularCategoryStates.toPersistentMap().put(subCategory, content))
    if (subCategory != popularSubCategory) return updated
    val mirrored = updated.copy(categoryStates = categoryStates.toPersistentMap().put(HomeCategory.POPULAR, content))
    return if (currentCategory == HomeCategory.POPULAR) mirrored.projectCurrentHomeContent() else mirrored
}

internal fun HomeUiState.updateHomeCategoryContent(
    category: HomeCategory,
    transform: (CategoryContent) -> CategoryContent,
): HomeUiState {
    if (category == HomeCategory.POPULAR) return updateHomePopularContent(popularSubCategory, transform)
    val updated = copy(categoryStates = categoryStates.toPersistentMap().put(category, transform(homeCategoryContent(category))))
    return if (category == currentCategory) updated.projectCurrentHomeContent() else updated
}

/** 每份缓存只过滤一次，热门镜像从已过滤的选中子分类生成。 */
internal fun HomeUiState.mapHomeCategoryContent(
    transform: (HomeCategory, PopularSubCategory?, CategoryContent) -> CategoryContent,
): HomeUiState {
    val categories = categoryStates.toPersistentMap().builder()
    categoryStates.forEach { (category, content) ->
        if (category != HomeCategory.POPULAR) categories[category] = transform(category, null, content)
    }
    val popular = popularCategoryStates.mapValues { (subCategory, content) ->
        transform(HomeCategory.POPULAR, subCategory, content)
    }.toPersistentMap()
    categories[HomeCategory.POPULAR] = popular[popularSubCategory] ?: CategoryContent()
    return copy(categoryStates = categories.build(), popularCategoryStates = popular).projectCurrentHomeContent()
}
