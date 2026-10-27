package com.dzirent.app.util

import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortTest {

    private fun listing(id: String, km: Double? = null, price: Double? = null, created: String = "2026-10-01T00:00:00Z", status: String = "active") =
        Listing(
            id = id, lenderId = "b", title = id, category = "Everyday", priceUsd = price, distanceKm = km,
            approxLat = 0.0, approxLng = 0.0, status = status, createdAt = created
        )

    @Test
    fun nearestPutsUnknownDistanceLast() {
        val sorted = sortSearchResults(listOf(listing("x"), listing("far", km = 9.0), listing("near", km = 1.0)), SearchSort.NEAREST)
        assertEquals(listOf("near", "far", "x"), sorted.map { it.id })
    }

    @Test
    fun newestFirst() {
        val sorted = sortSearchResults(
            listOf(listing("old", created = "2026-10-01T00:00:00Z"), listing("new", created = "2026-10-20T00:00:00Z")),
            SearchSort.NEWEST
        )
        assertEquals(listOf("new", "old"), sorted.map { it.id })
    }

    @Test
    fun priceSortsPutUnpricedLast() {
        val items = listOf(listing("none"), listing("cheap", price = 10.0), listing("dear", price = 80.0))
        assertEquals(listOf("cheap", "dear", "none"), sortSearchResults(items, SearchSort.PRICE_LOW).map { it.id })
        assertEquals(listOf("dear", "cheap", "none"), sortSearchResults(items, SearchSort.PRICE_HIGH).map { it.id })
    }

    @Test
    fun closetSorts() {
        val items = listOf(
            listing("paused-new", status = "paused", created = "2026-10-20T00:00:00Z"),
            listing("active-old", created = "2026-10-01T00:00:00Z"),
            listing("active-new", created = "2026-10-10T00:00:00Z")
        )
        assertEquals(listOf("paused-new", "active-new", "active-old"), sortCloset(items, ClosetSort.NEWEST).map { it.id })
        assertEquals(listOf("active-old", "active-new", "paused-new"), sortCloset(items, ClosetSort.OLDEST).map { it.id })
        assertEquals(listOf("active-new", "active-old", "paused-new"), sortCloset(items, ClosetSort.STATUS).map { it.id })
    }

    @Test
    fun chipsForEveryFilterThatIsSet() {
        val filters = BrowseFilters(
            category = Categories.ACCESSORIES, itemType = "Tie", radiusKm = 25,
            minPrice = 10.0, maxPrice = 50.0, size = "M"
        )
        assertEquals(listOf("Accessories", "Tie", "25 km", "\$10 - \$50", "Size M"), filterChips(filters).map { it.label })
        assertTrue(filterChips(BrowseFilters()).isEmpty())
    }

    @Test
    fun removingAChipClearsOnlyThatFilter() {
        val filters = BrowseFilters(category = Categories.ACCESSORIES, itemType = "Tie", maxPrice = 50.0)
        val chips = filterChips(filters)
        assertEquals(BrowseFilters(maxPrice = 50.0), chips.first { it.label == "Accessories" }.remove(filters))
        assertEquals(filters.copy(itemType = null), chips.first { it.label == "Tie" }.remove(filters))
        assertEquals(filters.copy(maxPrice = null), chips.first { it.label == "Up to \$50" }.remove(filters))
    }

    @Test
    fun recentSearchesNewestFirstWithoutDuplicates() {
        val recent = listOf("gown", "Saree", "tie")
        assertEquals(listOf("saree", "gown", "tie"), addRecentSearch(recent, " saree "))
        assertEquals(recent, addRecentSearch(recent, "  "))
        assertEquals(RECENT_SEARCHES_MAX, addRecentSearch((1..8).map { "q$it" }, "new").size)
    }
}
