package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateRangeTest {

    private fun range(start: String, end: String) = DateRange(LocalDate.parse(start), LocalDate.parse(end))

    @Test
    fun separateRangesDoNotOverlap() {
        assertFalse(range("2026-11-01", "2026-11-05").overlaps(range("2026-11-06", "2026-11-10")))
    }

    @Test
    fun sharedEndDayOverlaps() {
        // Both ends are inclusive, so the same day can't be in two ranges.
        assertTrue(range("2026-11-01", "2026-11-05").overlaps(range("2026-11-05", "2026-11-10")))
    }

    @Test
    fun containedRangeOverlaps() {
        assertTrue(range("2026-11-01", "2026-11-30").overlaps(range("2026-11-10", "2026-11-12")))
        assertTrue(range("2026-11-10", "2026-11-12").overlaps(range("2026-11-01", "2026-11-30")))
    }

    @Test
    fun singleDayRanges() {
        assertTrue(range("2026-11-03", "2026-11-03").overlaps(range("2026-11-03", "2026-11-03")))
        assertFalse(range("2026-11-03", "2026-11-03").overlaps(range("2026-11-04", "2026-11-04")))
    }

    @Test
    fun firstOverlapFindsTheConflict() {
        val existing = listOf(range("2026-11-01", "2026-11-05"), range("2026-11-20", "2026-11-25"))
        assertEquals(existing[1], firstOverlap(range("2026-11-24", "2026-11-28"), existing))
        assertNull(firstOverlap(range("2026-11-06", "2026-11-19"), existing))
    }

    @Test
    fun formatsReadably() {
        assertEquals("Nov 3 - Nov 10", range("2026-11-03", "2026-11-10").format())
        assertEquals("Nov 3 - Nov 10", formatDateRange("2026-11-03", "2026-11-10"))
    }

    @Test
    fun rejectsBadInput() {
        assertNull(parseDateRange("2026-11-10", "2026-11-03"))
        assertNull(parseDateRange("not a date", "2026-11-03"))
        assertNull(parseDateRange(null, "2026-11-03"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun endBeforeStartIsInvalid() {
        range("2026-11-10", "2026-11-03")
    }
}
