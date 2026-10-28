package com.dzirent.app.util

import com.dzirent.app.data.model.Listing

/** Sort options in search results (icon-only Sort button). */
enum class SearchSort(val label: String) {
    NEAREST("Nearest"),
    NEWEST("Newest"),
    PRICE_LOW("Price low-high"),
    PRICE_HIGH("Price high-low")
}

/** Sort options in Closet (icon-only Sort button). */
enum class ClosetSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    STATUS("Status")
}

/** Search results in [sort] order. Unknown distances and prices go last. */
fun sortSearchResults(listings: List<Listing>, sort: SearchSort): List<Listing> = when (sort) {
    SearchSort.NEAREST -> listings.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    SearchSort.NEWEST -> listings.sortedByDescending { timestampMillis(it.createdAt) }
    SearchSort.PRICE_LOW -> listings.sortedWith(compareBy<Listing> { it.priceUsd == null }.thenBy { it.priceUsd })
    SearchSort.PRICE_HIGH -> listings.sortedWith(compareBy<Listing> { it.priceUsd == null }.thenByDescending { it.priceUsd })
}

/** My listings in [sort] order. Status puts active before paused, newest first within each. */
fun sortCloset(listings: List<Listing>, sort: ClosetSort): List<Listing> = when (sort) {
    ClosetSort.NEWEST -> listings.sortedByDescending { timestampMillis(it.createdAt) }
    ClosetSort.OLDEST -> listings.sortedBy { timestampMillis(it.createdAt) }
    ClosetSort.STATUS -> listings.sortedWith(
        compareBy<Listing> { it.status != "active" }.thenByDescending { timestampMillis(it.createdAt) }
    )
}

/** One removable chip above search results; [remove] returns the filters without it. */
data class FilterChipItem(val label: String, val remove: (BrowseFilters) -> BrowseFilters)

/**
 * Chips for every filter that is set. Distance shows only when it isn't the default
 * distance from Settings ([defaultStep]); removing it goes back to that default.
 */
fun filterChips(
    filters: BrowseFilters,
    unit: DistanceUnit = DistanceUnit.MILES,
    defaultStep: Int = DEFAULT_DISTANCE_STEP
): List<FilterChipItem> = buildList {
    filters.category?.let { add(FilterChipItem(it) { f -> f.withCategory(null) }) }
    filters.itemType?.let { add(FilterChipItem(it) { f -> f.copy(itemType = null) }) }
    if (filters.distanceStep != defaultStep) {
        add(FilterChipItem(unit.optionLabel(filters.distanceStep)) { f -> f.copy(distanceStep = defaultStep) })
    }
    val min = filters.minPrice
    val max = filters.maxPrice
    val price = when {
        min != null && max != null -> "${formatPrice(min)} - ${formatPrice(max)}"
        min != null -> "From ${formatPrice(min)}"
        max != null -> "Up to ${formatPrice(max)}"
        else -> null
    }
    price?.let { add(FilterChipItem(it) { f -> f.copy(minPrice = null, maxPrice = null) }) }
    filters.size?.let { add(FilterChipItem("Size $it") { f -> f.copy(size = null) }) }
}

/** Most recent searches kept on the device. */
const val RECENT_SEARCHES_MAX = 8

/** [query] moved to the front of [recent], without case-insensitive duplicates, at most [max]. */
fun addRecentSearch(recent: List<String>, query: String, max: Int = RECENT_SEARCHES_MAX): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return recent
    return (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(max)
}
