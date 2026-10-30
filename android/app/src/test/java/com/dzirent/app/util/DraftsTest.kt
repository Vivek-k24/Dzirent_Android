package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DraftsTest {

    private val draft = SavedDraft(
        step = 2,
        photos = mapOf("front" to "file:///data/listing_photos/u/a.jpg", "close_up" to "file:///data/listing_photos/u/c.jpg"),
        kept = listOf("file:///data/listing_photos/u/a.jpg"),
        title = "Maroon lehenga",
        category = "Cultural & Traditional",
        description = "Maroon lehenga.",
        styles = listOf("South Asian"),
        fields = mapOf("title" to FieldState(FieldOrigin.SUGGESTED, "Maroon lehenga", "lehenga")),
        recognizedKind = "saree",
        sizeLabel = "Blouse 36",
        measurements = listOf(DraftMeasurement("Blouse bust (flat)", "18", template = true)),
        priceText = "40",
        ranges = listOf(DraftRange("2026-10-30", "2026-11-28")),
        aiReviewNeeded = true
    )

    @Test
    fun aDraftRoundTrips() {
        val back = SavedDraft.decode(SavedDraft.encode(draft))
        assertEquals(draft, back)
        assertEquals(listOf(DateRange(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 28))), back!!.dateRanges)
    }

    @Test
    fun onlyDraftsWithSomethingInThemAreOffered() {
        assertTrue(draft.hasContent)
        assertFalse(SavedDraft().hasContent)
        assertTrue(SavedDraft(priceText = "25").hasContent)
        assertFalse(SavedDraft(measurements = listOf(DraftMeasurement("Chest (flat)", "", template = true))).hasContent)
    }

    @Test
    fun aBrokenOrOldDraftIsIgnored() {
        assertNull(SavedDraft.decode(null))
        assertNull(SavedDraft.decode("{not json"))
        assertNull(SavedDraft.decode(SavedDraft.encode(draft.copy(version = 99))))
        // Unknown fields from a newer app don't break it.
        assertEquals("x", SavedDraft.decode("""{"version":1,"title":"x","somethingNew":true}""")!!.title)
    }
}
