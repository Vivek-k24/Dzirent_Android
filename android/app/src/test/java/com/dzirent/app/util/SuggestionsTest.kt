package com.dzirent.app.util

import com.dzirent.app.data.model.Availability
import com.dzirent.app.data.model.Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SuggestionsTest {

    private val today = LocalDate.parse("2026-10-28")
    private val dates = DateRange(today, today.plusDays(4)) // Oct 28 - Nov 1

    private fun listing(
        id: String,
        title: String = id,
        category: String = "Everyday",
        km: Double = 1.0,
        price: Double? = 20.0,
        size: String = "M",
        available: Pair<String, String>? = null,
        lender: String = "ben"
    ) = Listing(
        id = id, lenderId = lender, title = title, category = category, sizeLabel = size,
        priceUsd = price, approxLat = 0.0, approxLng = 0.0, status = "active", distanceKm = km,
        availability = listOfNotNull(available?.let { Availability("a$id", id, it.first, it.second) })
    )

    private fun input(
        query: String = "",
        filters: BrowseFilters = BrowseFilters(),
        nearby: List<Listing> = emptyList(),
        wider: List<Listing> = emptyList(),
        similar: List<Listing> = emptyList(),
        related: RelatedMatch? = null,
        dates: DateRange? = this.dates
    ) = SuggestionInput(query, filters, dates, "me", nearby, wider, similar, related, DistanceUnit.MILES, today)

    private val lehengaMatch = RelatedMatch("lehenga", listOf("ghagra", "saree"), "Cultural & Traditional")

    @Test
    fun stepsComeInOrder() {
        val nearby = listOf(
            listing("saree", title = "Silk saree", category = "Cultural & Traditional"),
            listing("kurta", title = "Kurta set", category = "Cultural & Traditional"),
            listing("later", title = "Lehenga later", category = "Cultural & Traditional", available = "2026-11-05" to "2026-11-20"),
            listing("party", title = "Lehenga party", category = "Occasion & Party", price = 60.0)
        )
        // The loosened steps keep the query, so they only help a correctly spelled one.
        val groups = buildSuggestions(
            input(
                query = "lehenga",
                filters = BrowseFilters(category = "Cultural & Traditional", maxPrice = 30.0),
                nearby = nearby,
                wider = nearby + listing("far", title = "Lehenga far", category = "Cultural & Traditional", km = 20.0),
                similar = listOf(listing("sim", title = "Red lehenga", category = "Cultural & Traditional")),
                related = lehengaMatch
            )
        )
        assertEquals(
            listOf(
                SuggestionStep.SIMILAR, SuggestionStep.RELATED, SuggestionStep.CATEGORY,
                SuggestionStep.OTHER_DATES, SuggestionStep.WIDER_DISTANCE
            ),
            groups.map { it.step }
        )
        assertEquals(
            listOf("Similar to ‘lehenga’", "Related to lehenga", "More in Cultural & Traditional", "Available Nov 5 - 9", "Within 15 miles"),
            groups.map { it.reason }
        )
        assertEquals(listOf("sim"), groups[0].listings.map { it.id })
        assertEquals(listOf("saree"), groups[1].listings.map { it.id })
        assertEquals(listOf("kurta"), groups[2].listings.map { it.id })
        assertEquals(listOf("later"), groups[3].listings.map { it.id })
        assertEquals(listOf("far"), groups[4].listings.map { it.id })
    }

    @Test
    fun loosenedStepsKeepTheOtherFilters() {
        val nearby = listOf(
            listing("pricey", title = "Lehenga gold", category = "Cultural & Traditional", price = 37.5),
            listing("other-cat", title = "Lehenga skirt", category = "Everyday", price = 10.0),
            listing("big", title = "Lehenga big", category = "Cultural & Traditional", price = 10.0, size = "XL")
        )
        val groups = buildSuggestions(
            input(
                query = "lehenga",
                filters = BrowseFilters(category = "Cultural & Traditional", maxPrice = 30.0, size = "M", distanceStep = 3),
                nearby = nearby
            )
        )
        assertEquals(
            listOf(SuggestionStep.HIGHER_PRICE, SuggestionStep.ALL_CATEGORIES, SuggestionStep.ANY_SIZE),
            groups.map { it.step }
        )
        assertEquals(listOf("Up to \$40", "All categories", "Any size"), groups.map { it.reason })
        assertEquals(listOf("pricey"), groups[0].listings.map { it.id })
        assertEquals(listOf("other-cat"), groups[1].listings.map { it.id })
        assertEquals(listOf("big"), groups[2].listings.map { it.id })
    }

    @Test
    fun stopsAtTwelve() {
        val many = (1..20).map { listing("s$it", title = "Saree $it", km = it.toDouble()) }
        val groups = buildSuggestions(input(query = "lehnga", nearby = many, similar = many.take(5), related = lehengaMatch))
        assertEquals(listOf(SuggestionStep.SIMILAR, SuggestionStep.RELATED), groups.map { it.step })
        assertEquals(SUGGESTIONS_MAX, groups.sumOf { it.listings.size })
        // No piece twice: related skips the five already shown as similar.
        assertEquals(listOf("s6", "s7"), groups[1].listings.take(2).map { it.id })
    }

    @Test
    fun similarResultsOutsideTheDistanceAreLeftOut() {
        val groups = buildSuggestions(
            input(query = "lehnga", similar = listOf(listing("far", title = "Lehenga", km = 500.0)), nearby = listOf(listing("x")))
        )
        assertEquals(listOf(SuggestionStep.NEAR_YOU), groups.map { it.step })
    }

    @Test
    fun neverEmptyWhenAnythingIsNearby() {
        val groups = buildSuggestions(input(query = "zzz", nearby = listOf(listing("b", km = 3.0), listing("a", km = 1.0))))
        assertEquals(listOf(SuggestionStep.NEAR_YOU), groups.map { it.step })
        assertEquals("Near you", groups.single().reason)
        assertEquals(listOf("a", "b"), groups.single().listings.map { it.id })
    }

    @Test
    fun myOwnListingsAreNeverSuggested() {
        val groups = buildSuggestions(input(query = "zzz", nearby = listOf(listing("mine", lender = "me"))))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun anyDatesSkipsTheOtherDatesStep() {
        val groups = buildSuggestions(
            input(query = "lehenga", dates = null, nearby = listOf(listing("x", title = "Lehenga", category = "Everyday")),
                filters = BrowseFilters(category = "Outerwear"))
        )
        assertEquals(listOf(SuggestionStep.ALL_CATEGORIES), groups.map { it.step })
    }

    @Test
    fun myStylesComeFirstWithinAGroupButNothingIsDropped() {
        val nearby = listOf(
            listing("near", km = 1.0),
            listing("styled-far", km = 5.0).copy(listingStyles = listOf("South Asian")),
            listing("mid", km = 3.0)
        )
        val ranked = buildSuggestions(input(query = "zzz", nearby = nearby).copy(styleInterests = setOf("South Asian")))
        assertEquals(listOf("styled-far", "near", "mid"), ranked.single().listings.map { it.id })
        val plain = buildSuggestions(input(query = "zzz", nearby = nearby))
        assertEquals(listOf("near", "mid", "styled-far"), plain.single().listings.map { it.id })
    }

    @Test
    fun styleOverlapCounts() {
        assertEquals(1, styleOverlap(listOf("South Asian", "Western formal"), setOf("South Asian")))
        assertEquals(0, styleOverlap(listOf("South Asian"), emptySet()))
    }

    @Test
    fun priceRoundsUpToFiveDollars() {
        assertEquals(40.0, roundUpPrice(37.5), 0.0)
        assertEquals(40.0, roundUpPrice(40.0), 0.0)
        assertEquals(5.0, roundUpPrice(0.5), 0.0)
    }

    @Test
    fun distanceLabels() {
        assertEquals("15 miles", DistanceUnit.MILES.withinLabel(3))
        assertEquals("1 mile", DistanceUnit.MILES.withinLabel(0))
        assertEquals("25 km", DistanceUnit.KILOMETERS.withinLabel(3))
    }
}
