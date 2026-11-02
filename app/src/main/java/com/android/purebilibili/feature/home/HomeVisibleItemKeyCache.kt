package com.android.purebilibili.feature.home

/** Lazy item keys are unique. Pixel-only scrolling reuses the published, immutable set. */
internal class HomeVisibleItemKeyCache {
    private var cachedKeys: Set<Any> = emptySet()

    fun <T> keys(items: List<T>, keyOf: (T) -> Any): Set<Any> {
        val previous = cachedKeys
        if (items.size == previous.size) {
            var unchanged = true
            for (index in items.indices) {
                if (keyOf(items[index]) !in previous) {
                    unchanged = false
                    break
                }
            }
            if (unchanged) return previous
        }
        // Never mutate a set already returned to a derived state or an older snapshot.
        val updated = HashSet<Any>(items.size)
        for (index in items.indices) updated.add(keyOf(items[index]))
        cachedKeys = updated
        return updated
    }
}
