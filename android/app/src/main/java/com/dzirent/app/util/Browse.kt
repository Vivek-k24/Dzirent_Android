package com.dzirent.app.util

import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Listing

/**
 * What Home and Search show, set from the sidebar (category) and its filter sheet.
 * Null means "any". Prices are in USD per rental.
 */
data class BrowseFilters(
    val category: String? = null,
    /** Only used with the Accessories category. */
    val itemType: String? = null,
    val radiusKm: Int = DEFAULT_DISTANCE_KM,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    /** Matched against the listing's size label, ignoring case and spaces. */
    val size: String? = null
) {
    /** Changing the category drops an item type that only made sense for Accessories. */
    fun withCategory(newCategory: String?): BrowseFilters =
        copy(category = newCategory, itemType = itemType.takeIf { newCategory == Categories.ACCESSORIES })
}

private fun normalizeSize(text: String): String = text.lowercase().filterNot { it.isWhitespace() }

/**
 * True if [sizeLabel] means [wanted]: the whole label, or one part of a label like
 * "M / US 8" (parts split on / , |), ignoring case and spaces. "8" doesn't match "US 8".
 */
fun sizeMatches(sizeLabel: String, wanted: String): Boolean {
    val target = normalizeSize(wanted)
    if (target.isEmpty()) return true
    if (normalizeSize(sizeLabel) == target) return true
    return sizeLabel.split('/', ',', '|').any { normalizeSize(it) == target }
}

/**
 * Category, item type, price and size filters. Distance is applied when loading. A listing
 * without a price never matches a price range, since its price is unknown.
 */
fun Listing.matchesFilters(filters: BrowseFilters): Boolean {
    if (filters.category != null && category != filters.category) return false
    if (filters.itemType != null && itemType != filters.itemType) return false
    if (filters.minPrice != null || filters.maxPrice != null) {
        val price = priceUsd ?: return false
        if (filters.minPrice != null && price < filters.minPrice) return false
        if (filters.maxPrice != null && price > filters.maxPrice) return false
    }
    if (filters.size != null && !sizeMatches(sizeLabel, filters.size)) return false
    return true
}

/** Title or description contains [query], ignoring case. Blank matches everything. */
fun Listing.matchesQuery(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || title.contains(q, ignoreCase = true) || description.contains(q, ignoreCase = true)
}
