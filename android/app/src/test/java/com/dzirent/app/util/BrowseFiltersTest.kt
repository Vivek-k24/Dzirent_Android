package com.dzirent.app.util

import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFiltersTest {

    private fun listing(
        category: String = "Everyday",
        itemType: String? = null,
        price: Double? = 25.0,
        size: String = "M / US 8",
        title: String = "Linen shirt",
        description: String = "Light and breezy"
    ) = Listing(
        id = "l", lenderId = "b", title = title, category = category, itemType = itemType,
        sizeLabel = size, description = description, priceUsd = price,
        approxLat = 0.0, approxLng = 0.0, status = "active"
    )

    @Test
    fun noFiltersMatchEverything() {
        assertTrue(listing().matchesFilters(BrowseFilters()))
    }

    @Test
    fun categoryAndItemType() {
        val sunglasses = listing(category = Categories.ACCESSORIES, itemType = "Sunglasses")
        assertTrue(sunglasses.matchesFilters(BrowseFilters(category = Categories.ACCESSORIES)))
        assertTrue(sunglasses.matchesFilters(BrowseFilters(category = Categories.ACCESSORIES, itemType = "Sunglasses")))
        assertFalse(sunglasses.matchesFilters(BrowseFilters(category = Categories.ACCESSORIES, itemType = "Tie")))
        assertFalse(sunglasses.matchesFilters(BrowseFilters(category = "Everyday")))
    }

    @Test
    fun priceRangeIsInclusive() {
        val filters = BrowseFilters(minPrice = 20.0, maxPrice = 25.0)
        assertTrue(listing(price = 20.0).matchesFilters(filters))
        assertTrue(listing(price = 25.0).matchesFilters(filters))
        assertFalse(listing(price = 25.01).matchesFilters(filters))
        assertFalse(listing(price = 19.99).matchesFilters(filters))
    }

    @Test
    fun listingWithoutPriceIsOutsideAnyPriceRange() {
        assertFalse(listing(price = null).matchesFilters(BrowseFilters(maxPrice = 100.0)))
        assertTrue(listing(price = null).matchesFilters(BrowseFilters()))
    }

    @Test
    fun sizeMatchesWholeLabelOrOnePart() {
        assertTrue(sizeMatches("M / US 8", "m"))
        assertTrue(sizeMatches("M / US 8", "US8"))
        assertTrue(sizeMatches("M / US 8", "m/us 8"))
        assertFalse(sizeMatches("M / US 8", "8"))
        assertFalse(sizeMatches("Medium", "M"))
    }

    @Test
    fun queryLooksAtTitleAndDescription() {
        assertTrue(listing().matchesQuery("linen"))
        assertTrue(listing().matchesQuery("BREEZY"))
        assertTrue(listing().matchesQuery("  "))
        assertFalse(listing().matchesQuery("gown"))
    }

    @Test
    fun changingCategoryDropsItemType() {
        val filters = BrowseFilters(category = Categories.ACCESSORIES, itemType = "Tie")
        assertNull(filters.withCategory("Everyday").itemType)
        assertEquals("Tie", filters.withCategory(Categories.ACCESSORIES).itemType)
    }
}
