package com.dzirent.app.util

import com.dzirent.app.data.model.Availability
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.ListingMeasurement
import com.dzirent.app.data.model.PrivateProfile
import com.dzirent.app.data.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class HomeSectionsTest {

    private val now = OffsetDateTime.parse("2026-10-27T12:00:00Z")
    private val dates = DateRange(LocalDate.parse("2026-10-27"), LocalDate.parse("2026-10-31"))
    private val noBody = PrivateProfile(userId = "me")

    private fun listing(
        id: String,
        lender: String = "ben",
        km: Double = 1.0,
        created: String = "2026-10-01T00:00:00Z",
        available: Pair<String, String>? = null,
        measurements: List<ListingMeasurement> = emptyList()
    ) = Listing(
        id = id, lenderId = lender, title = id, category = "Everyday",
        approxLat = 0.0, approxLng = 0.0, status = "active", createdAt = created, distanceKm = km,
        availability = listOfNotNull(available?.let { Availability("a$id", id, it.first, it.second) }),
        measurements = measurements, lender = Profile(id = lender, displayName = lender.replaceFirstChar { it.uppercase() })
    )

    private fun build(listings: List<Listing>, body: PrivateProfile = noBody, saved: Set<String> = emptySet()) =
        buildHomeSections(listings, "me", BrowseFilters(), dates, body, saved, now)

    @Test
    fun myOwnListingsAreLeftOut() {
        val sections = build(listOf(listing("a"), listing("mine", lender = "me")))
        assertEquals(listOf("a"), sections.visible.map { it.id })
    }

    @Test
    fun listingsMustBeAvailableForTheWholeRange() {
        val sections = build(
            listOf(
                listing("covers", available = "2026-10-25" to "2026-11-05"),
                listing("partial", available = "2026-10-29" to "2026-11-05"),
                listing("anytime")
            )
        )
        assertEquals(setOf("covers", "anytime"), sections.visible.map { it.id }.toSet())
    }

    @Test
    fun newNearYouIsTheLastSevenDaysNearestFirst() {
        val sections = build(
            listOf(
                listing("far-new", km = 9.0, created = "2026-10-26T00:00:00Z"),
                listing("near-new", km = 2.0, created = "2026-10-21T00:00:00Z"),
                listing("old", km = 0.5, created = "2026-10-19T00:00:00Z")
            )
        )
        assertEquals(listOf("near-new", "far-new"), sections.newNearYou.map { it.id })
    }

    @Test
    fun savedStudiosRow() {
        val sections = build(listOf(listing("a", lender = "ben"), listing("b", lender = "cy")), saved = setOf("cy"))
        assertEquals(listOf("b"), sections.fromSavedStudios.map { it.id })
    }

    @Test
    fun fitRowIsNullWithoutMeasurements() {
        assertNull(build(listOf(listing("a"))).likelyFit)
    }

    @Test
    fun fitRowUsesTheFitRule() {
        val body = PrivateProfile(userId = "me", chestCm = 90.0, waistCm = 72.0)
        fun m(label: String, value: Double) = ListingMeasurement(label, "x", label, value, "cm")
        val fits = listing("fits", measurements = listOf(m("Chest (flat)", 48.0), m("Waist (flat)", 40.0)))
        val tight = listing("tight", measurements = listOf(m("Chest (flat)", 40.0), m("Waist (flat)", 30.0)))
        assertEquals(listOf("fits"), build(listOf(fits, tight), body = body).likelyFit?.map { it.id })
    }

    @Test
    fun studiosGroupByLenderNearestFirst() {
        val sections = build(
            listOf(listing("a", lender = "ben", km = 5.0), listing("b", lender = "ben", km = 3.0), listing("c", lender = "cy", km = 1.0))
        )
        assertEquals(listOf("cy" to 1, "ben" to 2), sections.studios.map { it.lender.id to it.pieceCount })
        assertEquals(3.0, sections.studios[1].distanceKm!!, 0.0)
    }
}
