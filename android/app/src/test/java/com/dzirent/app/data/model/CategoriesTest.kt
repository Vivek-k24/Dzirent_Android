package com.dzirent.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun accessoriesReplacesBagsAndAccessories() {
        assertTrue(Categories.ACCESSORIES in Categories.ALL)
        assertTrue("Bags & Accessories" !in Categories.ALL)
    }

    @Test
    fun accessorySuggestionsFollowItemType() {
        assertEquals(
            listOf("Lens width", "Bridge", "Temple length"),
            Categories.measurementSuggestions(Categories.ACCESSORIES, "Sunglasses")
        )
        assertEquals(
            listOf("Case diameter", "Band length"),
            Categories.measurementSuggestions(Categories.ACCESSORIES, "Watch (non-smart)")
        )
    }

    @Test
    fun accessoriesWithoutItemTypeHaveNoSuggestions() {
        assertEquals(emptyList<String>(), Categories.measurementSuggestions(Categories.ACCESSORIES, null))
    }

    @Test
    fun otherCategoriesIgnoreItemType() {
        assertEquals(
            listOf("Chest", "Length", "Waist", "Inseam"),
            Categories.measurementSuggestions("Everyday", "Sunglasses")
        )
    }

    @Test
    fun everyItemTypeHasSuggestions() {
        AccessoryTypes.ALL.forEach { type ->
            assertTrue(type, Categories.measurementSuggestions(Categories.ACCESSORIES, type).isNotEmpty())
        }
    }
}
