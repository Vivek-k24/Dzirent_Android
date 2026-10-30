package com.dzirent.app.util

import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.ListingMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListingFormTest {

    private val lehenga = suggestionsFor(ItemLabels.byName("lehenga")!!, ColorResult(ColorName.MAROON))
    private val blank = SuggestField.entries.associateWith { "" }

    // ---- Suggest once ---------------------------------------------------------------------

    @Test
    fun emptyUntouchedFieldsGetTheSuggestion() {
        val fills = fieldsToFill(emptyMap(), blank, lehenga)
        assertEquals("Maroon lehenga", fills[SuggestField.TITLE])
        assertEquals("Cultural & Traditional", fills[SuggestField.CATEGORY])
        assertEquals("Maroon lehenga.", fills[SuggestField.DESCRIPTION])
        assertEquals("South Asian", fills[SuggestField.STYLE])
    }

    @Test
    fun suggestOnceNeverOverwrites() {
        val label = ItemLabels.byName("lehenga")!!
        val states = mapOf(
            SuggestField.TITLE to suggestedState("Red saree", label),        // filled earlier
            SuggestField.CATEGORY to FieldState(FieldOrigin.CLEARED),        // the lender cleared it
            SuggestField.DESCRIPTION to FieldState(FieldOrigin.EDITED)       // the lender typed, then emptied by hand later
        )
        val current = blank + (SuggestField.TITLE to "Red saree")
        val fills = fieldsToFill(states, current, lehenga)
        assertEquals(setOf(SuggestField.STYLE), fills.keys)
        // A field the lender typed in is never filled, even if a suggestion would fit.
        assertEquals(emptyMap<SuggestField, String>(), fieldsToFill(emptyMap(), current + (SuggestField.STYLE to "x") + (SuggestField.CATEGORY to "y") + (SuggestField.DESCRIPTION to "z"), lehenga))
    }

    @Test
    fun editsAndClearsAreRemembered() {
        val s = suggestedState("Maroon lehenga", ItemLabels.byName("lehenga")!!)
        assertEquals(FieldOrigin.EDITED, editedState(s, "Maroon lehenga, size M").origin)
        assertEquals(FieldOrigin.CLEARED, editedState(s, "").origin)
        assertEquals(FieldOrigin.CLEARED, clearedState(s).origin)
        assertEquals("Maroon lehenga", clearedState(s).suggested)
    }

    @Test
    fun pickingAChipFillsTitleCategoryAndStyleButNotWhatWasTyped() {
        val blazer = suggestionsForChip(ItemLabels.byName("blazer")!!, ColorResult(ColorName.NAVY))
        val states = mapOf(
            SuggestField.TITLE to suggestedState("Suit", ItemLabels.byName("suit")!!),
            SuggestField.CATEGORY to FieldState(FieldOrigin.CLEARED),
            SuggestField.STYLE to FieldState(FieldOrigin.EDITED)
        )
        val current = blank + (SuggestField.TITLE to "Suit") + (SuggestField.STYLE to "Western casual")
        val fills = fieldsToFillForChip(states, current, blazer)
        assertEquals("Navy blazer", fills[SuggestField.TITLE])
        assertEquals("Career & Professional", fills[SuggestField.CATEGORY])
        assertNull(fills[SuggestField.STYLE])
        assertEquals("Navy blazer.", fills[SuggestField.DESCRIPTION])
    }

    @Test
    fun outcomesAtPublish() {
        val s = suggestedState("Maroon lehenga", ItemLabels.byName("lehenga")!!)
        assertEquals("kept", suggestionOutcome(s, "Maroon lehenga"))
        assertEquals("kept", suggestionOutcome(s, " Maroon lehenga "))
        assertEquals("edited", suggestionOutcome(editedState(s, "x"), "Maroon bridal lehenga"))
        assertEquals("cleared", suggestionOutcome(clearedState(s), ""))
        assertNull(suggestionOutcome(FieldState(FieldOrigin.EDITED), "My own title"))
    }

    // ---- Steps -----------------------------------------------------------------------------

    private fun step2(
        size: String = "M",
        price: String = "25",
        accessory: Boolean = false,
        ticked: Boolean = false,
        retail: String = "",
        ranges: List<DateRange> = listOf(defaultAvailability(LocalDate.of(2026, 10, 30)))
    ) = Step2Input(size, price, accessory, ticked, retail, "", "", true, ranges, hasLocation = true)

    @Test
    fun categoryStartsBlankAndIsRequired() {
        assertEquals("Choose a category", step1Missing(null, "Maroon lehenga", null, null, false)!!.button)
        assertEquals(CHOOSE_CATEGORY, "Choose a category")
        assertNull(step1Missing(null, "Maroon lehenga", "Cultural & Traditional", null, false))
    }

    @Test
    fun step1NamesWhatsMissing() {
        val photos = Missing("Add 1 more photo", "…")
        assertEquals("Add 1 more photo", step1Missing(photos, "", null, null, false)!!.button)
        assertEquals("Add a title", step1Missing(null, "ab", "Everyday", null, false)!!.button)
        assertEquals("Choose an item type", step1Missing(null, "Black clutch", "Accessories", null, false)!!.button)
        assertEquals("Sign the declaration", step1Missing(null, "Black clutch", "Accessories", "Handbag", true)!!.button)
    }

    @Test
    fun step2NamesWhatsMissing() {
        assertNull(step2Missing(step2()))
        assertEquals("Choose a size", step2Missing(step2(size = ""))!!.button)
        assertEquals("Add a price", step2Missing(step2(price = ""))!!.button)
        assertEquals("Add a price", step2Missing(step2(price = "0"))!!.button)
        assertEquals("Add dates", step2Missing(step2(ranges = emptyList()))!!.button)
    }

    @Test
    fun accessoriesNeedTheTickAndAValueUnder300() {
        assertEquals("Tick the declaration", step2Missing(step2(accessory = true))!!.button)
        assertNull(step2Missing(step2(accessory = true, ticked = true, retail = "300")))
        val tooMuch = step2Missing(step2(accessory = true, ticked = true, retail = "300.01"))!!
        assertEquals(ACCESSORY_VALUE_TOO_HIGH, tooMuch.message)
        assertEquals(ACCESSORY_VALUE_TOO_HIGH, accessoryValueError(true, "450"))
        // Clothing can be worth more.
        assertNull(accessoryValueError(false, "450"))
        assertNull(step2Missing(step2(retail = "450")))
    }

    @Test
    fun theReadyList() {
        val list = readyList(step1Done = true, input = step2(price = ""))
        assertEquals(listOf("Photos and details", "Size", "Price", "Dates"), list.map { it.label })
        assertEquals(listOf(true, true, false, true), list.map { it.done })
        assertEquals("Declaration", readyList(true, step2(accessory = true)).last().label)
    }

    // ---- Dates -----------------------------------------------------------------------------

    @Test
    fun datesStartAsTheNext30Days() {
        val today = LocalDate.of(2026, 10, 30)
        val range = defaultAvailability(today)
        assertEquals(today, range.start)
        assertEquals(today.plusDays(29), range.end)
        assertEquals("Next 30 days", availabilityChipLabel(range, today))
        assertEquals("Nov 3 - Nov 10", availabilityChipLabel(DateRange(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10)), today))
    }

    // ---- Kind, measurements, "Same as your …" ---------------------------------------------------

    private fun listing(id: String, kind: GarmentKind?, created: String, measured: Boolean = true) = Listing(
        id = id, lenderId = "me", title = "Piece $id", category = "Everyday", approxLat = 0.0, approxLng = 0.0, status = "active",
        createdAt = created, garmentType = kind?.key,
        measurements = if (measured) listOf(ListingMeasurement("m$id", id, "Waist (flat)", 16.0, "in")) else emptyList()
    )

    @Test
    fun sameAsOffersTheMostRecentListingOfTheSameKind() {
        val mine = listOf(
            listing("old", GarmentKind.TROUSERS, "2026-09-01T10:00:00Z"),
            listing("new", GarmentKind.TROUSERS, "2026-10-01T10:00:00Z"),
            listing("unmeasured", GarmentKind.TROUSERS, "2026-10-20T10:00:00Z", measured = false),
            listing("dress", GarmentKind.DRESS, "2026-10-25T10:00:00Z")
        )
        val pick = sameAsCandidate(mine, GarmentKind.TROUSERS, excludeId = null)!!
        assertEquals("new", pick.id)
        assertEquals("Same as your Piece new?", sameAsQuestion(pick))
        assertEquals(listOf("Waist (flat)" to 16.0) to "in", copiedMeasurements(pick))
        // Not the listing being edited, and nothing without a kind.
        assertEquals("old", sameAsCandidate(mine, GarmentKind.TROUSERS, excludeId = "new")!!.id)
        assertNull(sameAsCandidate(mine, null, null))
        assertNull(sameAsCandidate(mine, GarmentKind.SUIT, null))
    }

    @Test
    fun kindDecidesTheMeasurementRows() {
        assertEquals(listOf("Waist (flat)", "Inseam", "Rise", "Leg opening"), measurementLabelsFor(GarmentKind.TROUSERS, null))
        assertEquals(listOf("Lens width", "Bridge", "Temple length"), measurementLabelsFor(GarmentKind.ONE_SIZE, "Sunglasses"))
        assertEquals(emptyList<String>(), measurementLabelsFor(null, null))
        assertEquals(GarmentKind.ONE_SIZE, garmentKindFor(null, null, "Accessories"))
        assertEquals(GarmentKind.DRESS, garmentKindFor(GarmentKind.TOP, GarmentKind.DRESS, "Everyday"))
        assertEquals(GarmentKind.TOP, garmentKindFor(GarmentKind.TOP, null, "Everyday"))
        assertNull(garmentKindFor(null, null, "Everyday"))
        assertTrue(GarmentKind.entries.all { GarmentKind.fromKey(it.key) == it })
    }
}
