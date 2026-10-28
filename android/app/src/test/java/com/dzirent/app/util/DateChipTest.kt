package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateChipTest {

    private fun d(text: String) = LocalDate.parse(text)
    private fun r(start: String, end: String) = DateRange(d(start), d(end))

    @Test
    fun defaultIsTodayThroughFourDaysLater() {
        assertEquals(r("2026-10-26", "2026-10-30"), defaultBrowseRange(d("2026-10-26")))
    }

    @Test
    fun defaultCrossingAMonth() {
        assertEquals(r("2026-10-29", "2026-11-02"), defaultBrowseRange(d("2026-10-29")))
    }

    @Test
    fun sameMonth() {
        assertEquals("Oct 26 - 30", formatDateChip(r("2026-10-26", "2026-10-30")))
    }

    @Test
    fun differentMonths() {
        assertEquals("Oct 26 - Nov 1", formatDateChip(r("2026-10-26", "2026-11-01")))
    }

    @Test
    fun differentYears() {
        assertEquals("Dec 30 - Jan 2", formatDateChip(r("2026-12-30", "2027-01-02")))
    }

    @Test
    fun sameMonthInDifferentYearsIsNotSameMonth() {
        assertEquals("Oct 26 - Oct 30", formatDateChip(r("2026-10-26", "2027-10-30")))
    }

    @Test
    fun singleDay() {
        assertEquals("Oct 26", formatDateChip(r("2026-10-26", "2026-10-26")))
    }

    @Test
    fun defaultChipNeverSaysAnyDate() {
        val chip = formatDateChip(defaultBrowseRange(d("2026-12-30")))
        assertEquals("Dec 30 - Jan 3", chip)
        assertFalse(chip.contains("Any", ignoreCase = true))
    }

    @Test
    fun noWindowsMeansAvailableAnyTime() {
        assertTrue(isAvailableFor(emptyList(), r("2026-10-26", "2026-10-30")))
    }

    @Test
    fun oneWindowMustCoverTheWholeRange() {
        val windows = listOf(r("2026-10-25", "2026-10-30"))
        assertTrue(isAvailableFor(windows, r("2026-10-26", "2026-10-30")))
        assertTrue(isAvailableFor(windows, r("2026-10-25", "2026-10-25")))
        assertFalse(isAvailableFor(windows, r("2026-10-26", "2026-10-31")))
        assertFalse(isAvailableFor(windows, r("2026-10-24", "2026-10-26")))
    }

    @Test
    fun backToBackWindowsAreJoined() {
        val windows = listOf(r("2026-10-27", "2026-10-31"), r("2026-10-20", "2026-10-26"))
        assertTrue(isAvailableFor(windows, r("2026-10-25", "2026-10-28")))
    }

    @Test
    fun overlappingWindowsAreJoined() {
        val windows = listOf(r("2026-10-20", "2026-10-28"), r("2026-10-25", "2026-11-02"), r("2026-10-21", "2026-10-22"))
        assertTrue(isAvailableFor(windows, r("2026-10-21", "2026-11-02")))
    }

    @Test
    fun aGapBreaksCoverage() {
        val windows = listOf(r("2026-10-20", "2026-10-25"), r("2026-10-27", "2026-10-31"))
        assertFalse(isAvailableFor(windows, r("2026-10-24", "2026-10-28")))
        assertTrue(isAvailableFor(windows, r("2026-10-27", "2026-10-28")))
    }

    @Test
    fun datesQuestionHasTheActualDates() {
        assertEquals("Is this available Oct 27 - 31?", datesQuestion(r("2026-10-27", "2026-10-31")))
        assertEquals("Is this available Oct 30 - Nov 2?", datesQuestion(r("2026-10-30", "2026-11-02")))
        assertEquals("Is this available Oct 27?", datesQuestion(r("2026-10-27", "2026-10-27")))
    }

    @Test
    fun sentQuestionSavesItsDates() {
        val dates = r("2026-10-27", "2026-10-31")
        assertEquals(dates, datesAsked(datesQuestion(dates), dates))
        assertEquals(dates, datesAsked("Hi! Is this available Oct 27 - 31? Thanks", dates))
        assertNull(datesAsked("Is this available Nov 3 - 5?", dates))
        assertNull(datesAsked("Can I see more photos?", dates))
    }

    @Test
    fun chatDatesLineNamesWhoAsked() {
        val dates = r("2026-10-27", "2026-10-31")
        assertEquals("Your dates: Oct 27 - 31", chatDatesLine(dates, iAmRenter = true))
        assertEquals("Their dates: Oct 27 - 31", chatDatesLine(dates, iAmRenter = false))
        assertNull(chatDatesLine(null, iAmRenter = true))
    }
}
